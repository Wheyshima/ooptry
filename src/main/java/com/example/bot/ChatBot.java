package com.example.bot;

import com.example.bot.command.CommandRegistry;
import com.example.bot.command.impl.*;
import com.example.bot.database.DatabaseManager;
import com.google.gson.JsonArray;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import com.example.bot.database.DatabaseManager.UserWithCity;

@SuppressWarnings("deprecation")
public class ChatBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(ChatBot.class);
    private final String botUsername;
    private final String botToken;
    private final CommandRegistry commandRegistry;
    private final DatabaseManager databaseManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> editStartTimes = new ConcurrentHashMap<>();
    private final TodoCommand todoCommand;


    private static final long EDIT_TIMEOUT_MS = 10000; // 10 секунд таймаут
    private static final long CLEANUP_INITIAL_DELAY_MINUTES = 1;
    private static final long CLEANUP_PERIOD_MINUTES = 1;
    private static final String WEATHER_API_KEY = "b3d108dc2567f3da1587c2d2392be91d";
    private static final OkHttpClient httpClient = new OkHttpClient();

    public ChatBot(String botUsername, String botToken, DatabaseManager databaseManager) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.databaseManager = databaseManager;
        this.commandRegistry = new CommandRegistry();
        this.todoCommand = new TodoCommand(databaseManager, this);

        initializeCommands();
        registerBotCommands();
        cleanupOnStartup();
        startDailyCleanupTask();
        startEditTimeoutCleanup();
        scheduleMorningWeather();
    }
    private void scheduleMorningWeather() {
        LocalTime now = LocalTime.now();
        LocalTime target = LocalTime.of(18, 21); // 7:00 утра каждый день
        long initialDelayMinutes;

        if (now.isBefore(target)) {
            initialDelayMinutes = now.until(target, ChronoUnit.MINUTES);
        } else {
            initialDelayMinutes = now.until(target.plusHours(24), ChronoUnit.MINUTES);
        }

        scheduler.scheduleAtFixedRate(
                this::sendMorningWeather,
                initialDelayMinutes,
                24 * 60,
                TimeUnit.MINUTES
        );
        logger.info("⏰ Утренняя погодная рассылка запланирована на {} (через {} мин)", target, initialDelayMinutes);
    }

    private void sendMorningWeather() {
        try {
            logger.info("🌤 Запуск утренней погодной рассылки");

            // 1. Пользователи с городами
            List<UserWithCity> usersWithCity = databaseManager.getAllUsersWithCities();
            logger.debug("Найдено {} пользователей с городом", usersWithCity.size());

            for (UserWithCity user : usersWithCity) {
                try {
                    String weatherReport = buildWeatherReport(user.city());
                    execute(SendMessage.builder()
                            .chatId(user.userId())
                            .text(weatherReport)
                            .parseMode("HTML") // важно: HTML для <b>
                            .build());
                } catch (Exception e) {
                    logger.error("Ошибка отправки погоды пользователю {}", user.userId(), e);
                }
            }

            // 2. Все пользователи
            Set<Long> allUserIds = new HashSet<>(databaseManager.getAllUserIds());
            Set<Long> usersWithCitySet = usersWithCity.stream()
                    .map(UserWithCity::userId)
                    .collect(Collectors.toSet());

            // 3. Напоминания тем, у кого нет города
            int remindersSent = 0;
            for (Long userId : allUserIds) {
                if (!usersWithCitySet.contains(userId)) {
                    try {
                        execute(SendMessage.builder()
                                .chatId(userId)
                                .text("🌤 Укажите ваш город для утренней сводки погоды!\nПример: /setcity Москва")
                                .parseMode("Markdown")
                                .build());
                        remindersSent++;
                    } catch (Exception e) {
                        logger.error("Ошибка отправки напоминания пользователю {}", userId, e);
                    }
                }
            }

            logger.info("Утренняя рассылка завершена: {} погодных сводок, {} напоминаний",
                    usersWithCity.size(), remindersSent);

        } catch (Exception e) {
            logger.error("Критическая ошибка в утренней рассылке", e);
        }
    }
    private String buildWeatherReport(String city) {
        try {
            if (city == null || city.trim().isEmpty()) {
                return "❌ Сначала укажите город через /setcity";
            }

            String cleanCity = city.trim();
            String encodedCity = URLEncoder.encode(cleanCity, StandardCharsets.UTF_8);
            String geocodeUrl = "http://api.openweathermap.org/geo/1.0/direct?q=" + encodedCity + "&limit=1&appid=" + WEATHER_API_KEY;

            // === Шаг 1: Проверяем, существует ли город ===
            Request geoRequest = new Request.Builder().url(geocodeUrl).build();
            try (Response geoResponse = httpClient.newCall(geoRequest).execute()) {
                String geoBody = geoResponse.body().string();
                if (!geoResponse.isSuccessful() || geoBody.trim().equals("[]")) {
                    return "❌ Город \"" + cleanCity + "\" не найден.\nПопробуйте: /setcity Moscow или /setcity Москва,RU";
                }

                JsonArray geoArray = JsonParser.parseString(geoBody).getAsJsonArray();
                if (geoArray.isEmpty()) {
                    return "❌ Город не найден: \"" + cleanCity + "\"";
                }

                JsonObject loc = geoArray.get(0).getAsJsonObject();
                double lat = loc.get("lat").getAsDouble();
                double lon = loc.get("lon").getAsDouble();
                String resolvedCity = loc.has("local_names") && loc.getAsJsonObject("local_names").has("ru")
                        ? loc.getAsJsonObject("local_names").get("ru").getAsString()
                        : loc.get("name").getAsString();

                // === Шаг 2: Получаем текущую погоду через forecast (первый элемент = сейчас) ===
                String forecastUrl = "https://api.openweathermap.org/data/2.5/forecast?" +
                        "lat=" + lat + "&lon=" + lon +
                        "&units=metric&lang=ru&appid=" + WEATHER_API_KEY;

                Request forecastRequest = new Request.Builder().url(forecastUrl).build();
                try (Response forecastResponse = httpClient.newCall(forecastRequest).execute()) {
                    if (!forecastResponse.isSuccessful()) {
                        return "❌ Ошибка загрузки погоды для " + resolvedCity;
                    }

                    JsonObject root = JsonParser.parseString(forecastResponse.body().string()).getAsJsonObject();
                    JsonArray list = root.getAsJsonArray("list");
                    if (list.isEmpty()) {
                        return "🌤 Погода в " + resolvedCity + " недоступна";
                    }

                    JsonObject current = list.get(0).getAsJsonObject();
                    double temp = current.getAsJsonObject("main").get("temp").getAsDouble();
                    String desc = current.getAsJsonArray("weather").get(0)
                            .getAsJsonObject().get("description").getAsString();

                    return String.format(
                            "<b>Погода в %s сейчас</b>\n" +
                                    "<b>Температура:</b> %.1f°C\n" +
                                    "<b>Описание:</b> %s",
                            resolvedCity, temp, desc
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка погоды для города: " + city, e);
            return "⚠️ Не удалось загрузить погоду. Попробуйте позже.";
        }
    }



    private void cleanupOnStartup() {
        try {
            logger.info(" Проверка устаревших задач при запуске...");
            DatabaseManager.TaskStats stats = databaseManager.getTaskStats();

            if (stats.oldTasks > 0) {
                logger.info("🗑️ Найдено {} задач предыдущих дней, очищаем...", stats.oldTasks);
                performCleanupOperations();
            } else {
                logger.info(" Нет устаревших задач для очистки");
            }

            logger.info(" Сегодняшних задач: {}", stats.todayTasks);

        } catch (Exception e) {
            logger.error(" Ошибка при очистке при запуске:", e);
        }
    }

    private void performCleanupOperations() {
        databaseManager.cleanupOldProductivityStats();
        databaseManager.saveAllUsersProductivityStats();
        databaseManager.cleanupAllDailyTasks();
        databaseManager.cleanupUnlockedWishes();
    }

    private void initializeCommands() {
        commandRegistry.registerCommand(new StartCommand());
        commandRegistry.registerCommand(new SetCityCommand(databaseManager));
        commandRegistry.registerCommand(todoCommand);
        commandRegistry.registerCommand(new WishlistCommand(databaseManager));
        commandRegistry.registerCommand(new StatsCommand(databaseManager));
        commandRegistry.registerCommand(new AboutCommand());
        commandRegistry.registerCommand(new AuthorsCommand());
        commandRegistry.registerCommand(new HelpCommand(commandRegistry));

        logger.info(" Инициализировано {} команд", commandRegistry.getCommandCount());
    }

    private void registerBotCommands() {
        try {
            execute(SetMyCommands.builder()
                    .commands(commandRegistry.getBotCommands())
                    .build());
            logger.info(" Команды бота успешно зарегистрированы в меню");
        } catch (TelegramApiException e) {
            logger.error(" Ошибка при регистрации команд меню", e);
        }
    }

    private void startDailyCleanupTask() {
        ZoneId utcPlusTwo = ZoneId.of("Asia/Yekaterinburg");
        ZonedDateTime nowInUtcPlusTwo = ZonedDateTime.now(utcPlusTwo);
        // Устанавливаем очистку на 23:59 сегодня или завтра, если время уже прошло
        ZonedDateTime nextCleanup = nowInUtcPlusTwo.toLocalDate()
                .atTime(23, 59)
                .atZone(utcPlusTwo);

        if (nowInUtcPlusTwo.isAfter(nextCleanup)) {
            nextCleanup = nextCleanup.plusDays(1);
        }

        long initialDelay = Duration.between(nowInUtcPlusTwo, nextCleanup).getSeconds();

        logger.info("""
            ⏰ Настройка ежедневной очистки:
               Текущее время сервера: {}
               Текущее время UTC+5: {}
               Следующая очистка: {}
               Задержка до очистки: {} секунд ({} часов)""",
            LocalDateTime.now(),
            nowInUtcPlusTwo,
            nextCleanup,
            initialDelay,
            String.format("%.2f", initialDelay / 3600.0));



        scheduler.scheduleAtFixedRate(
                this::performDailyCleanup,
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
    }

    private void performDailyCleanup() {
        try {
            ZoneId utcPlusTwo = ZoneId.of("Asia/Yekaterinburg");
            ZonedDateTime cleanupTime = ZonedDateTime.now(utcPlusTwo);
            logger.info(" Запуск ежедневной очистки задач в {} (UTC+5)", cleanupTime);
            performCleanupOperations();

            DatabaseManager.TaskStats stats = databaseManager.getTaskStats();
            logger.info(" До очистки: {} всего, {} устаревших, {} сегодняшних",
                    stats.totalTasks, stats.oldTasks, stats.todayTasks);

            int todayTasksAfter = databaseManager.getTodayTasksCount();
            logger.info(" После очистки: {} сегодняшних задач сохранено", todayTasksAfter);
            logger.info(" Ежедневная очистка завершена");

        } catch (Exception e) {
            logger.error(" Ошибка при ежедневной очистке", e);
        }
    }

    /**
     * Запускает периодическую очистку устаревших состояний редактирования
     */
    private void startEditTimeoutCleanup() {
        scheduler.scheduleAtFixedRate(
                this::cleanupExpiredEditStates,
                CLEANUP_INITIAL_DELAY_MINUTES,
                CLEANUP_PERIOD_MINUTES,
                TimeUnit.MINUTES
        );
        logger.debug("Запущена периодическая очистка устаревших состояний редактирования");
    }

    /**
     * Очищает устаревшие состояния редактирования
     */
    private void cleanupExpiredEditStates() {
        long currentTime = System.currentTimeMillis();

        editStartTimes.entrySet().removeIf(entry -> {
            Long userId = entry.getKey();
            Long startTime = entry.getValue();

            if (startTime != null && (currentTime - startTime) > EDIT_TIMEOUT_MS) {
                cleanupEditState(userId);
                sendTimeoutNotification(userId);
                return true;
            }
            return false;
        });
    }

    /**
     * Отправляет уведомление о таймауте пользователю
     */
    private void sendTimeoutNotification(Long userId) {
        String message = """
        ⏰ *Время редактирования истекло*
        
        Редактирование задачи автоматически отменено через 5 секунд бездействия.
        Для повторного редактирования используйте команду `/todo edit` с нужным ID задачи.""";

        SendMessage timeoutMessage = SendMessage.builder()
                .chatId(userId.toString())
                .text(message)
                .parseMode("Markdown")
                .build();

        try {
            execute(timeoutMessage);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке уведомления о таймауте пользователю {}", userId, e);        }
    }

    @Override
    public void onClosing() {
        logger.info("Завершение работы бота...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warn("Принудительное завершение планировщика");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Поток был прерван при завершении работы");
        }
        super.onClosing();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage());
        }
    }

    private void handleMessage(org.telegram.telegrambots.meta.api.objects.Message message) {
        try {
            Long userId = message.getFrom().getId();
            String text = message.getText();

            databaseManager.saveUser(userId, message.getFrom().getUserName());

            // Проверяем таймаут перед обработкой состояния
            if (userStates.containsKey(userId) && isEditTimedOut(userId)) {
                cleanupEditState(userId);
                sendTimeoutMessage(message.getChatId());
                return;
            }

            if (userStates.containsKey(userId)) {
                handleUserState(userId, text, message);
                return;
            }

            com.example.bot.command.Command command = commandRegistry.findCommandForMessage(message);

            if (command != null) {
                String response = command.execute(message);
                sendResponse(message.getChatId(), response);
            } else {
                sendResponse(message.getChatId(),
                        "Неизвестная команда. Используйте /help для просмотра доступных команд.");
            }

        } catch (Exception e) {
            logger.error("Ошибка обработки сообщения от пользователя {}", message.getFrom().getId(), e);
            sendResponse(message.getChatId(), "Произошла ошибка при обработке команды.");
        }
    }

    private void sendTimeoutMessage(Long chatId) {
        sendResponse(chatId, """
        ⏰ *Время редактирования истекло*
        
        Редактирование задачи автоматически отменено через 5 секунд бездействия.
        Для повторного редактирования используйте команду `/todo edit` с нужным ID задачи.""");
    }

    private void handleUserState(Long userId, String text, org.telegram.telegrambots.meta.api.objects.Message message) {
        UserState state = userStates.get(userId);

        if (isCancelCommand(text)) {
            cleanupEditState(userId);
            sendResponse(message.getChatId(), " Действие отменено.");
            return;
        }

        try {
            String response = processUserState(userId, text, state);
            sendResponse(message.getChatId(), response);

        } catch (Exception e) {
            logger.error("Ошибка при обработке состояния пользователя {}", userId, e);
            cleanupEditState(userId);
            sendResponse(message.getChatId(), " Произошла ошибка при обработке. Состояние сброшено.");
        }
    }

    private boolean isCancelCommand(String text) {
        return text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel");
    }

    private String processUserState(Long userId, String text, UserState state) {
        cleanupEditState(userId); // Всегда очищаем состояние после обработки

        return switch (state.getType()) {
            case EDITING_TODO_TASK -> todoCommand.handleEditInput(userId, state.getTaskId(), text);
            case SETTING_CITY -> " Функция установки города временно недоступна.";
        };
    }

    /**
     * Запускает состояние редактирования задачи с отслеживанием времени
     */
    public void startTodoEditState(Long userId, int taskId) {
        userStates.put(userId, new UserState(StateType.EDITING_TODO_TASK, taskId));
        editStartTimes.put(userId, System.currentTimeMillis());
    }

    public boolean hasActiveState(Long userId) {
        return userStates.containsKey(userId);
    }

    public void cancelUserState(Long userId) {
        cleanupEditState(userId);
    }

    public boolean isEditTimedOut(Long userId) {
        Long startTime = editStartTimes.get(userId);
        if (startTime == null) return true;

        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > EDIT_TIMEOUT_MS;
    }

    public void cleanupEditState(Long userId) {
        userStates.remove(userId);
        editStartTimes.remove(userId);
    }

    private void sendResponse(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения в чат {}", chatId, e);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    private static class UserState {
        private final StateType type;
        private final int taskId;

        public UserState(StateType type, int taskId) {
            this.type = type;
            this.taskId = taskId;
        }

        public StateType getType() { return type; }
        public int getTaskId() { return taskId; }
    }

    private enum StateType {
        EDITING_TODO_TASK,
        SETTING_CITY
    }
}
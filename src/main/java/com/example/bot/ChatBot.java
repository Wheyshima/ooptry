package com.example.bot;

import com.example.bot.command.CommandRegistry;
import com.example.bot.command.impl.*;
import com.example.bot.database.DatabaseManager;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.example.bot.database.DatabaseManager.UserWithCity;


public class ChatBot extends TelegramLongPollingBot {
    private final String botUsername;
    private final String botToken;
    private final CommandRegistry commandRegistry;
    private final DatabaseManager databaseManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final Map<Long, UserState> userStates = new HashMap<>();
    private final TodoCommand todoCommand;

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
        scheduleMorningWeather();
    }

    private void scheduleMorningWeather() {
        // Рассчитываем, сколько ждать до 7:00
        LocalTime now = LocalTime.now();
        LocalTime target = LocalTime.of(2, 52);
        long initialDelayMinutes;

        if (now.isBefore(target)) {
            initialDelayMinutes = now.until(target, ChronoUnit.MINUTES);
        } else {
            // Уже прошли 7:00 — ждём до завтра
            initialDelayMinutes = now.until(target.plusHours(24), ChronoUnit.MINUTES);
        }

        scheduler.scheduleAtFixedRate(
                this::sendMorningWeather,
                initialDelayMinutes,
                24 * 60, // повтор каждые 24 часа
                TimeUnit.MINUTES
        );
    }

    private void sendMorningWeather() {
        try {
            // 1. Получаем всех пользователей с городами
            List<DatabaseManager.UserWithCity> usersWithCity = databaseManager.getAllUsersWithCities();
            for (DatabaseManager.UserWithCity user : usersWithCity) {
                String weatherReport = buildWeatherReport(user.city());
                execute(SendMessage.builder()
                        .chatId(user.userId())
                        .text(weatherReport)
                        .build());
            }
            // 2. Получаем ВСЕХ пользователей (или только тех, кто писал боту)
            Set<Long> allUserIds = new HashSet<>(databaseManager.getAllUserIds());
            Set<Long> usersWithCitySet = usersWithCity.stream()
                    .map(UserWithCity::userId)
                    .collect(Collectors.toSet());

            // 3. Отправляем напоминание тем, у кого нет города
            for (Long userId : allUserIds) {
                if (!usersWithCitySet.contains(userId)) {
                    execute(SendMessage.builder()
                            .chatId(userId)
                            .text("🌤 Укажите ваш город для утренней сводки погоды!\nПример: /setcity Москва")
                            .build());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String buildWeatherReport(String city) {
        try {
            // Пример: запрос к OpenWeather API (Current или Forecast)
            // Формат: "🌤 Погода в Москве на сегодня:\nУтро: +10°C, ☀️\nДень: +15°C, ⛅\n..."

            // На первом этапе можно просто заглушку:
            return String.format("🌤 Погода в %s на сегодня:\nУтро: ...\nДень: ...\nВечер: ...\nНочь: ...", city);

        } catch (Exception e) {
            return "❌ Не удалось загрузить погоду для " + city + ". Попробуйте позже.";
        }
    }


            private void cleanupOnStartup() {
        try {
            System.out.println("🔍 Проверка устаревших задач при запуске...");

            // Используем публичные методы DatabaseManager
            DatabaseManager.TaskStats stats = databaseManager.getTaskStats();

            if (stats.oldTasks > 0) {
                System.out.println("🗑️ Найдено " + stats.oldTasks + " задач предыдущих дней, очищаем...");
                databaseManager.cleanupExpiredDailyTasks();
            } else {
                System.out.println("✅ Нет устаревших задач для очистки");
            }

            System.out.println("📅 Сегодняшних задач: " + stats.todayTasks);

        } catch (Exception e) {
            System.err.println("  Ошибка при очистке при запуске: " + e.getMessage());
        }
    }

    private void initializeCommands() {
        CommandRegistry registry = this.commandRegistry;

        registry.registerCommand(new StartCommand());
        registry.registerCommand(new AboutCommand());
        registry.registerCommand(new AuthorsCommand());

        registry.registerCommand(new SetCityCommand(databaseManager));
        registry.registerCommand(todoCommand);
        registry.registerCommand(new WishlistCommand(databaseManager));
        registry.registerCommand(new StatsCommand(databaseManager));
        registry.registerCommand(new CleanupCommand(databaseManager));
        registry.registerCommand(new ResetWishlistCommand(databaseManager));

        registry.registerCommand(new HelpCommand(registry));
    }

    private void registerBotCommands() {
        try {
            execute(SetMyCommands.builder()
                    .commands(commandRegistry.getBotCommands())
                    .build());
        } catch (TelegramApiException e) {
            System.err.println("Ошибка при регистрации команд меню: " + e.getMessage());
        }
    }

    private void startDailyCleanupTask() {
        ZoneId utcPlusTwo = ZoneId.of("Asia/Yekaterinburg");

        ZonedDateTime nowInUtcPlusTwo = ZonedDateTime.now(utcPlusTwo);
        ZonedDateTime nextMidnight = nowInUtcPlusTwo.toLocalDate()
                .plusDays(1)
                .atStartOfDay(utcPlusTwo);

        long initialDelay = Duration.between(nowInUtcPlusTwo, nextMidnight).getSeconds();

        // Детальная информация о времени
        System.out.println("⏰ Настройка ежедневной очистки:");
        System.out.println("   Текущее время сервера: " + LocalDateTime.now());
        System.out.println("   Текущее время UTC+5: " + nowInUtcPlusTwo);
        System.out.println("   Следующая очистка: " + nextMidnight);
        System.out.println("   Задержка до очистки: " + initialDelay + " секунд (" +
                String.format("%.2f часов", initialDelay / 3600.0) + ")");

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
            System.out.println("🔄 Запуск ежедневной очистки задач в " + cleanupTime + " (UTC+5)");

            // 1. Сохраняем статистику продуктивности ВСЕХ активных пользователей
            databaseManager.saveAllUsersProductivityStats();
            // Получим статистику ДО очистки
            DatabaseManager.TaskStats stats = databaseManager.getTaskStats();
            System.out.println("📊 До очистки: " +
                    stats.totalTasks + " всего, " +
                    stats.oldTasks + " устаревших, " +
                    stats.todayTasks + " сегодняшних");

            // Выполнить очистку (удалит только старые задачи)
            databaseManager.cleanupExpiredDailyTasks();

            // Очистка НЕзаблокированных желаний (НОВАЯ ФУНКЦИЯ)
            databaseManager.cleanupUnlockedWishes();

            // Очистка устаревших ЗАБЛОКИРОВАННЫХ желаний
            databaseManager.cleanupExpiredWishes();

            // Проверим результат после очистки
            int todayTasksAfter = databaseManager.getTodayTasksCount();
            System.out.println("📊 После очистки: " + todayTasksAfter + " сегодняшних задач сохранено");

            System.out.println("✅ Ежедневная очистка завершена");

        } catch (Exception e) {
            System.err.println("  Ошибка при ежедневной очистке: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onClosing() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
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

            databaseManager.saveUser(
                    message.getFrom().getId(),
                    message.getFrom().getUserName()
            );

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
            System.err.println("Ошибка обработки сообщения: " + e.getMessage());
            e.printStackTrace();
            sendResponse(message.getChatId(), "Произошла ошибка при обработке команды.");
        }
    }

    private void handleUserState(Long userId, String text, org.telegram.telegrambots.meta.api.objects.Message message) {
        UserState state = userStates.get(userId);

        if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
            userStates.remove(userId);
            sendResponse(message.getChatId(), "  Действие отменено.");
            return;
        }

        try {
            String response;
            switch (state.getType()) {
                case EDITING_TODO_TASK:
                    response = todoCommand.handleEditInput(userId, state.getTaskId(), text);
                    break;
                case SETTING_CITY:
                    response = "  Функция установки города временно недоступна.";
                    break;
                default:
                    response = "  Неизвестное состояние. Действие отменено.";
            }

            userStates.remove(userId);
            sendResponse(message.getChatId(), response);

        } catch (Exception e) {
            System.err.println("Ошибка при обработке состояния пользователя: " + e.getMessage());
            e.printStackTrace();
            userStates.remove(userId);
            sendResponse(message.getChatId(), "  Произошла ошибка при обработке. Состояние сброшено.");
        }
    }
///

///
    public void startTodoEditState(Long userId, int taskId) {
        userStates.put(userId, new UserState(StateType.EDITING_TODO_TASK, taskId));
    }

    public boolean hasActiveState(Long userId) {
        return userStates.containsKey(userId);
    }

    public void cancelUserState(Long userId) {
        userStates.remove(userId);
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
            System.err.println("Ошибка при отправке сообщения: " + e.getMessage());
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
package com.example.bot;

import com.example.bot.command.Command;
import com.example.bot.command.CommandRegistry;
import com.example.bot.command.impl.*;
import com.example.bot.database.DatabaseManager;
import com.example.bot.model.City;
import com.example.bot.model.JsonCity;
import com.example.bot.service.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.InputStream;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;


@SuppressWarnings("deprecation")
public class ChatBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(ChatBot.class);

    private final String botUsername;
    private final String botToken;
    private final DatabaseManager databaseManager;

    private final CommandRegistry commandRegistry;
    private final TodoCommand todoCommand;

    private final CityService cityService;

    private final MessageSender messageSender;
    private final UserStateService userStateService;
    private final TaskSchedulerService taskSchedulerService;

    public ChatBot(String botUsername, String botToken, DatabaseManager databaseManager,String weatherApiKey) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.databaseManager = databaseManager;
        List<City> cities = loadCitiesFromResource();
        this.cityService = new CityService(cities);
        this.commandRegistry = new CommandRegistry();
        MorningNewsletterService newsletterService = new MorningNewsletterService(databaseManager, this, weatherApiKey);
        this.messageSender = new TelegramMessageSender(this);

        this.userStateService = new UserStateService(cityService, databaseManager, messageSender);
        this.todoCommand = new TodoCommand(databaseManager, this.userStateService);
        this.userStateService.setTodoCommand(this.todoCommand);
        this.taskSchedulerService = new TaskSchedulerService(databaseManager, newsletterService, messageSender);

        initializeCommands();
        registerBotCommands();
        cleanupOnStartup();

        // Запуск фоновых задач
        taskSchedulerService.startAllTasks();
        userStateService.startEditTimeoutCleanup();
    }

    private void cleanupOnStartup() {
        try {
            logger.info("Проверка устаревших задач при запуске...");
            DatabaseManager.TaskStats stats = databaseManager.getTaskStats();

            if (stats.oldTasks > 0) {
                logger.info("🗑️ Найдено {} задач предыдущих дней, очищаем...", stats.oldTasks);
                performCleanupOperations();
            } else {
                logger.info("Нет устаревших задач для очистки");
            }

            logger.info("Сегодняшних задач: {}", stats.todayTasks);

        } catch (Exception e) {
            logger.error("Ошибка при очистке при запуске:", e);
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
        commandRegistry.registerCommand(new SetCityCommand(databaseManager,cityService));
        commandRegistry.registerCommand(todoCommand);
        commandRegistry.registerCommand(new WishlistCommand(databaseManager));
        commandRegistry.registerCommand(new StatsCommand(databaseManager));
        commandRegistry.registerCommand(new AboutCommand());
        commandRegistry.registerCommand(new AuthorsCommand());
        commandRegistry.registerCommand(new HelpCommand(commandRegistry));

        logger.info(" Инициализировано {} команд", commandRegistry.getCommandCount());
    }

    public static List<City> loadCitiesFromResource() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = ChatBot.class.getClassLoader()
                    .getResourceAsStream("cities_russia.json");

            if (is == null) {
                throw new RuntimeException("❌ cities_russia.json не найден в src/main/resources/");
            }

            List<JsonCity> rawCities = mapper.readValue(
                    is,
                    mapper.getTypeFactory().constructCollectionType(List.class, JsonCity.class)
            );

            return rawCities.stream()
                    .map(JsonCity::toCity)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("💥 Ошибка при загрузке списка городов", e);
        }
    }

    private void registerBotCommands() {
        try {
            execute(SetMyCommands.builder()
                    .commands(commandRegistry.getBotCommands())
                    .build());
            logger.info("Команды бота успешно зарегистрированы в меню");
        } catch (TelegramApiException e) {
            logger.error("Ошибка при регистрации команд меню", e);
        }
    }

    @Override
    public void onClosing() {
        logger.info("Завершение работы бота...");
        taskSchedulerService.shutdown();
        userStateService.shutdown();
        super.onClosing();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleMessage(Message message) {
        try {
            Long userId = message.getFrom().getId();
            String text = message.getText().trim();
            Long chatId = message.getChatId();

            databaseManager.saveUser(userId, message.getFrom().getUserName());

            // Таймаут редактирования
            if (userStateService.hasActiveState(userId) && userStateService.isEditTimedOut(userId)) {
                userStateService.cleanupEditState(userId);
                messageSender.sendText(chatId, """
                    ⏰ *Время редактирования истекло*
                    
                    Редактирование автоматически отменено через 10 секунд бездействия.
                    Попробуйте снова.""");
                return;
            }

            // Обработка состояний
            if (userStateService.hasActiveState(userId)) {
                userStateService.handleUserState(userId, text, chatId);
                return;
            }

            // Команда /setcity с аргументом
            if (text.startsWith("/setcity")) {
                handleSetCityCommand(message);
                return;
            }

            // Поиск команды
            var command = commandRegistry.findCommandForMessage(message);
            if (command != null) {
                String response = command.execute(message);
                if ("/stats".equals(text) || (text.startsWith("/stats ") && !text.contains("week"))) {
                    messageSender.sendTextWithInlineKeyboard(chatId, response, StatsCommand.getWeekStatsKeyboard());
                    return;
                }
                if ("/start".equals(text) || "/help".equals(text)) {
                    messageSender.sendTextWithKeyboard(chatId, response, KeyboardService.mainMenu());
                } else {
                    messageSender.sendText(chatId, response);
                }
                return;
            }

            messageSender.sendTextWithKeyboard(
                    chatId,
                    "Неизвестная команда. Используйте /help или выберите действие из меню.",
                    KeyboardService.mainMenu()
            );

        } catch (Exception e) {
            logger.error("Ошибка обработки сообщения от пользователя {}",
                    message.getFrom() != null ? message.getFrom().getId() : "unknown", e);
            messageSender.sendText(message.getChatId(), "Произошла ошибка при обработке команды.");
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            if (data.equals("change_city_yes")) {
                showCitySelectionMenu(chatId, userId);
            } else if (data.equals("change_city_no")) {
                messageSender.sendText(chatId, "✅ Изменение отменено.");
            } else if (data.startsWith("select_city:")) {
                String cityName = data.substring("select_city:".length());
                databaseManager.updateUserCity(userId, cityName);
                messageSender.sendText(chatId, "✅ Город установлен: *" + cityName + "*");
                userStateService.cancelUserState(userId);
            } else if (data.equals("select_city_manual")) {
                messageSender.sendText(chatId, "Введите название города вручную (только РФ):");
                userStateService.startCitySelectionState(userId);
            } else if (data.equals("stats:week")) {
                // Создаём фейковое сообщение для команды /stats week
                Message fakeMessage = new Message();
                fakeMessage.setChat(new Chat());
                fakeMessage.getChat().setId(chatId);
                fakeMessage.setFrom(new User());
                fakeMessage.getFrom().setId(userId);
                fakeMessage.setText("/stats week");

                Command command = commandRegistry.findCommandForMessage(fakeMessage);
                if (command != null) {
                    String response = command.execute(fakeMessage);
                    messageSender.sendText(chatId, response);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка обработки callback", e);
            messageSender.sendText(chatId, "Произошла ошибка. Попробуйте снова.");
        }
    }


    private String extractCommandArgument(String fullCommandText) {
        // Убираем команду и лишние пробелы
        String[] parts = fullCommandText.split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : "";
    }
    private void handleSetCityCommand(Message message) {
        Long userId = message.getFrom().getId();
        String currentCity = databaseManager.getUserCity(userId);
        String arg = extractCommandArgument(message.getText()).trim();

        // Если пользователь сразу ввёл город: /setcity Москва
        if (!arg.isEmpty()) {
            City matchedCity = cityService.findCity(arg);
            if (matchedCity != null) {
                databaseManager.updateUserCity(userId, matchedCity.getName());
                messageSender.sendText(
                        message.getChatId(),
                        String.format("✅ Город установлен: *%s*", matchedCity.getName())
                );
            } else {
                messageSender.sendText(
                        message.getChatId(),
                        "❌ Город не найден. Используйте меню или введите корректное название."
                );
            }
            return;
        }

        // Город уже установлен — спрашиваем, менять ли
        if (currentCity != null && !currentCity.isBlank()) {
            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboardRow(Arrays.asList(
                            InlineKeyboardButton.builder().text("Да").callbackData("change_city_yes").build(),
                            InlineKeyboardButton.builder().text("Нет").callbackData("change_city_no").build()
                    ))
                    .build();

            messageSender.sendTextWithInlineKeyboard(
                    message.getChatId(),
                    String.format("Ваш город: *%s*\n\nХотите изменить?", currentCity),
                    keyboard
            );
            return;
        }
        // Город не установлен — сразу показываем выбор
        showCitySelectionMenu(message.getChatId(), userId);
    }

    private void showCitySelectionMenu(Long chatId, Long userId) {
        List<String> topCities = cityService.getTop10Cities();
        List<InlineKeyboardButton> buttons = topCities.stream()
                .map(city -> InlineKeyboardButton.builder()
                        .text(city)
                        .callbackData("select_city:" + city)
                        .build())
                .toList();

        InlineKeyboardButton manualBtn = InlineKeyboardButton.builder()
                .text("✏️ Ввести вручную")
                .callbackData("select_city_manual")
                .build();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 2) {
            rows.add(buttons.subList(i, Math.min(i + 2, buttons.size())));
        }
        rows.add(List.of(manualBtn));

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();

        messageSender.sendTextWithInlineKeyboard(
                chatId,
                "Выберите город из списка или введите вручную:",
                keyboard
        );
        userStateService.startCitySelectionState(userId);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
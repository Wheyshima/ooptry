// com.example.bot/ChatBot.java
package com.example.bot;

import com.example.bot.command.CommandRegistry;
import com.example.bot.database.DatabaseManager;
import com.example.bot.model.City;
import com.example.bot.model.JsonCity;
import com.example.bot.service.*;
import com.example.bot.command.impl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

public class ChatBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(ChatBot.class);

    private final String botUsername;
    private final String botToken;
    private final DatabaseManager databaseManager;

    private final MessageHandlerService messageHandler;
    private final CallbackHandlerService callbackHandler;
    @SuppressWarnings("deprecation")
    public ChatBot(String botUsername, String botToken, DatabaseManager databaseManager, String weatherApiKey) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.databaseManager = databaseManager;

        // Инициализация зависимостей
        List<City> cities = loadCitiesFromResource();
        CityService cityService = new CityService(cities);
        WeatherService weatherService = new WeatherService(weatherApiKey);
        MessageSender messageSender = new TelegramMessageSender(this);
        CommandRegistry commandRegistry = new CommandRegistry();
        MorningNewsletterService newsletterService = new MorningNewsletterService(databaseManager, this, weatherApiKey);

        // Команды
        TodoCommand todoCommand = new TodoCommand(databaseManager, null); // UserStateService установим потом
        WishlistCommand wishlistCommand = new WishlistCommand(databaseManager);
        StatsCommand statsCommand = new StatsCommand(databaseManager, weatherService);
        SetCityCommand setCityCommand = new SetCityCommand(databaseManager, cityService, weatherService);

        // Регистрация команд
        commandRegistry.registerCommand(new StartCommand());
        commandRegistry.registerCommand(setCityCommand);
        commandRegistry.registerCommand(todoCommand);
        commandRegistry.registerCommand(wishlistCommand);
        commandRegistry.registerCommand(statsCommand);
        commandRegistry.registerCommand(new AboutCommand());
        commandRegistry.registerCommand(new AuthorsCommand());
        commandRegistry.registerCommand(new HelpCommand(commandRegistry));
        commandRegistry.registerCommand(new MenuCommand());

        // UserStateService
        UserStateService userStateService = new UserStateService(cityService, databaseManager, messageSender, commandRegistry);
        todoCommand.setUserStateService(userStateService);
        // Сервисы
        this.messageHandler = new MessageHandlerService(
                databaseManager, commandRegistry, messageSender, userStateService
        );
        this.callbackHandler = new CallbackHandlerService(
                databaseManager, commandRegistry, messageSender, userStateService, cityService
        );

        // Запуск фоновых задач
        TaskSchedulerService taskSchedulerService = new TaskSchedulerService(databaseManager, newsletterService, messageSender);
        taskSchedulerService.startAllTasks();
        userStateService.startEditTimeoutCleanup();

        // Инициализация
        initializeCommands(commandRegistry);
        registerBotCommands(commandRegistry);
        cleanupOnStartup();
    }

    private void initializeCommands(CommandRegistry commandRegistry) {
        logger.info("Инициализировано {} команд", commandRegistry.getCommandCount());
    }

    private void registerBotCommands(CommandRegistry commandRegistry) {
        try {
            execute(SetMyCommands.builder()
                    .commands(commandRegistry.getBotCommands())
                    .build());
            logger.info("Команды бота успешно зарегистрированы в меню");
        } catch (TelegramApiException e) {
            logger.error("Ошибка при регистрации команд меню", e);
        }
    }

    private void cleanupOnStartup() {
        try {
            logger.info("Проверка устаревших задач при запуске...");
            var stats = databaseManager.getTaskStats();
            if (stats.oldTasks > 0) {
                logger.info("🗑️ Найдено {} задач предыдущих дней, очищаем...", stats.oldTasks);
                performCleanupOperations();
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

    public static List<City> loadCitiesFromResource() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = ChatBot.class.getClassLoader().getResourceAsStream("cities_russia.json");
            if (is == null) {
                throw new RuntimeException("❌ cities_russia.json не найден в src/main/resources/");
            }
            List<JsonCity> rawCities = mapper.readValue(is, mapper.getTypeFactory().constructCollectionType(List.class, JsonCity.class));
            return rawCities.stream().map(JsonCity::toCity).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("💥 Ошибка при загрузке списка городов", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            messageHandler.handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            callbackHandler.handleCallback(update.getCallbackQuery());
        }
    }

    @Override
    public void onClosing() {
        logger.info("Завершение работы бота...");
        // Можно добавить shutdown для сервисов, если нужно
        super.onClosing();
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
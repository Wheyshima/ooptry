package com.example.bot;


import com.example.bot.database.DatabaseManager;
import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotApplication {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            Dotenv dotenv = Dotenv.load();
            // ЗАМЕНИТЕ на реальные данные вашего бота
            String botUsername = dotenv.get( "TELEGRAM_BOT_NAME");
            String botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
            if (botToken == null || botToken.isEmpty()) {
                throw new IllegalStateException("TELEGRAM_BOT_TOKEN environment variable is not set!");
            }

            String dbUrl = dotenv.get("DATABASE_URL", "jdbc:postgresql://localhost:5432/telegram_bot");
            String dbUsername = dotenv.get("DATABASE_USERNAME", "postgres");
            String dbPassword = dotenv.get("DATABASE_PASSWORD", "password");

            System.out.println("Подключение к базе данных: " + dbUrl);

            DatabaseManager databaseManager = new DatabaseManager(dbUrl, dbUsername, dbPassword);

            ChatBot bot = new ChatBot(botUsername, botToken, databaseManager);
            botsApi.registerBot(bot);

            System.out.println("Бот успешно запущен!");
            System.out.println("Бот: @" + botUsername);
            System.out.println("База данных: " + dbUrl);

        } catch (TelegramApiException e) {
            System.err.println("Ошибка при запуске: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalStateException e) {
            System.err.println("Ошибка конфигурации: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Неожиданная ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
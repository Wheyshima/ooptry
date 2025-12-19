package com.example.bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class BotApplication {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // ЗАМЕНИТЕ на реальные данные вашего бота
            String botUsername = "bot_vnuchacha";
            String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
            if (botToken == null || botToken.isEmpty()) {
                throw new IllegalStateException("TELEGRAM_BOT_TOKEN environment variable is not set!");
            }

            ChatBot bot = new ChatBot(botUsername, botToken);
            botsApi.registerBot(bot);

            System.out.println("Бот успешно запущен!");
            System.out.println("Бот: @" + botUsername);

        } catch (TelegramApiException e) {
            System.err.println("Ошибка при запуске: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
// com.example.bot.service/MessageHandlerService.java
package com.example.bot.service;

import com.example.bot.command.Command;
import com.example.bot.command.CommandRegistry;
import com.example.bot.database.DatabaseManager;
import com.example.bot.keyboard.InlineKeyboardFactory;
import org.telegram.telegrambots.meta.api.objects.Message;

public class MessageHandlerService {
    private final DatabaseManager databaseManager;
    private final CommandRegistry commandRegistry;
    private final MessageSender messageSender;
    private final UserStateService userStateService;

    public MessageHandlerService(
            DatabaseManager databaseManager,
            CommandRegistry commandRegistry,
            MessageSender messageSender,
            UserStateService userStateService
    ) {
        this.databaseManager = databaseManager;
        this.commandRegistry = commandRegistry;
        this.messageSender = messageSender;
        this.userStateService = userStateService;
    }

    public void handleMessage(Message message) {
        try {
            Long userId = message.getFrom().getId();
            String text = message.getText().trim();
            Long chatId = message.getChatId();

            databaseManager.saveUser(userId, message.getFrom().getUserName());

            if (userStateService.hasActiveState(userId) && userStateService.isEditTimedOut(userId)) {
                userStateService.cleanupEditState(userId);
                messageSender.sendText(chatId, """
                    ⏰ *Время редактирования истекло*
                    
                    Редактирование автоматически отменено через 10 секунд бездействия.
                    Попробуйте снова.""");
                return;
            }

            if (userStateService.hasActiveState(userId)) {
                userStateService.handleUserState(userId, text, chatId);
                return;
            }

            Command command = commandRegistry.findCommandForMessage(message);
            if (command != null) {
                System.out.println("Обработка команды '{}' для пользователя {}"+ text+ userId);
                try {
                    String response = command.execute(message);
                    sendResponseWithKeyboardIfNeeded(chatId, text, response, userId);
                } catch (Exception e) {
                    System.out.println("Ошибка при выполнении команды '{}' у пользователя {}"+ text+ userId+ e.getMessage());
                    messageSender.sendText(chatId, "Произошла ошибка при обработке команды.");
                }
                return;
            }

            messageSender.sendTextWithKeyboard(
                    chatId,
                    "Неизвестная команда. Используйте /help или выберите действие из меню.",
                    KeyboardService.mainMenu()
            );

        } catch (Exception e) {
            // Логирование можно добавить через отдельный Logger
            messageSender.sendText(message.getChatId(), "Произошла ошибка при обработке команды.");
        }
    }

    private void sendResponseWithKeyboardIfNeeded(Long chatId, String text, String response, Long userId) {
        if ("/start".equals(text) || "/help".equals(text) || "/menu".equals(text)) {
            messageSender.sendTextWithKeyboard(chatId, response, KeyboardService.mainMenu());
        } else if ("/todo".equals(text)) {
            messageSender.sendTextWithInlineKeyboard(chatId, response, InlineKeyboardFactory.getTodoActionsKeyboard());
        } else if ("/wishlist".equals(text)) {
            boolean isLocked = databaseManager.isWishlistLocked(userId);
            boolean hasWishes = !databaseManager.getWishes(userId).isEmpty();
            var keyboard = InlineKeyboardFactory.getWishlistActionsKeyboard(isLocked, hasWishes);
            messageSender.sendTextWithInlineKeyboard(chatId, response, keyboard);
        } else if ("/setcity".equals(text)) {
            String currentCity = databaseManager.getUserCity(userId);
            if (currentCity != null && !currentCity.isBlank()) {
                messageSender.sendTextWithInlineKeyboard(
                        chatId,
                        response,
                        InlineKeyboardFactory.getChangeCityConfirmationKeyboard()
                );
                return;
            }
            messageSender.sendText(chatId, response);
        } else if ("/stats".equals(text) || (text.startsWith("/stats ") && !text.contains("week"))) {
            messageSender.sendTextWithInlineKeyboard(chatId, response, InlineKeyboardFactory.getWeekStatsKeyboard());
        } else {
            messageSender.sendText(chatId, response);
        }
    }
}
package com.example.bot;

import com.example.bot.command.CommandRegistry;
import com.example.bot.command.impl.AboutCommand;
import com.example.bot.command.impl.AuthorsCommand;
import com.example.bot.command.impl.HelpCommand;
import com.example.bot.command.impl.StartCommand;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class ChatBot extends TelegramLongPollingBot {
    private final String botUsername;
    private final String botToken;
    private final CommandRegistry commandRegistry;

    public ChatBot(String botUsername, String botToken) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.commandRegistry = new CommandRegistry();

        initializeCommands();
        registerBotCommands();
    }

    private void initializeCommands() {
        CommandRegistry registry = this.commandRegistry;

        registry.registerCommand(new StartCommand());
        registry.registerCommand(new AboutCommand());
        registry.registerCommand(new AuthorsCommand());
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

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage());
        }
    }

    private void handleMessage(org.telegram.telegrambots.meta.api.objects.Message message) {
        com.example.bot.command.Command command = commandRegistry.findCommandForMessage(message);

        if (command != null) {
            String response = command.execute(message);
            sendResponse(message.getChatId(), response);
        } else {
            sendResponse(message.getChatId(),
                    "unknow команда. Используйте /help для просмотра доступных команд.");
        }
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
}
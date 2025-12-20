package com.example.bot.service;

import com.example.bot.ChatBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TelegramMessageSender implements MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(TelegramMessageSender.class);
    private final ChatBot chatBot;

    public TelegramMessageSender(ChatBot chatBot) {
        this.chatBot = chatBot;
    }

    @Override
    public void sendText(Long chatId, String text) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();
        try {
            chatBot.execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки сообщения в чат {}", chatId, e);
        }
    }

    @Override
    public void sendTextWithKeyboard(Long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
        try {
            chatBot.execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки сообщения с клавиатурой в чат {}", chatId, e);
        }
    }

    @Override
    public void sendTextWithInlineKeyboard(Long chatId, String text, InlineKeyboardMarkup inlineKeyboard) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(inlineKeyboard)
                .build();
        try {
            chatBot.execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки сообщения с inline-клавиатурой в чат {}", chatId, e);
        }
    }
}
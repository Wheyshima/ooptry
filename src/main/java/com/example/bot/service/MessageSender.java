// src/main/java/com/example/bot/service/MessageSender.java
package com.example.bot.service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

public interface MessageSender {
    void sendText(Long chatId, String text);
    void sendTextWithKeyboard(Long chatId, String text, ReplyKeyboardMarkup keyboard);
    void sendTextWithInlineKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard);
    void editMessageText(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard);
}

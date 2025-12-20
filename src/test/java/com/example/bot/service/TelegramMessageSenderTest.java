// com.example.bot.service/TelegramMessageSenderTest.java
package com.example.bot.service;

import com.example.bot.ChatBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

class TelegramMessageSenderTest {

    @Mock
    private ChatBot mockChatBot;

    private TelegramMessageSender messageSender;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        messageSender = new TelegramMessageSender(mockChatBot);
    }

    // ========= sendText =========

    @Test
    void sendText_callsExecuteWithCorrectSendMessage() throws TelegramApiException {
        // Given
        Long chatId = 123L;
        String text = "Привет, мир!";

        // When
        messageSender.sendText(chatId, text);

        // Then
        var captor = forClass(SendMessage.class);
        verify(mockChatBot).execute(captor.capture());

        SendMessage msg = captor.getValue();
        assertAll(
                () -> assertEquals("123", msg.getChatId()),
                () -> assertEquals("Привет, мир!", msg.getText()),
                () -> assertEquals("Markdown", msg.getParseMode()),
                () -> assertNull(msg.getReplyMarkup()) // нет клавиатуры
        );
    }

    @Test
    void sendText_handlesTelegramApiException() throws TelegramApiException {
        // Given
        doThrow(new TelegramApiException("API error")).when(mockChatBot).execute(any(SendMessage.class));

        // When
        messageSender.sendText(123L, "Текст");

        // Then: не должно быть исключения — только логирование (проверяем, что execute вызван)
        verify(mockChatBot).execute(any(SendMessage.class));
    }

    // ========= sendTextWithKeyboard =========

    @Test
    void sendTextWithKeyboard_callsExecuteWithReplyKeyboard() throws TelegramApiException {
        // Given
        Long chatId = 456L;
        String text = "Выберите действие:";
        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder().build();

        // When
        messageSender.sendTextWithKeyboard(chatId, text, keyboard);

        // Then
        var captor = forClass(SendMessage.class);
        verify(mockChatBot).execute(captor.capture());

        SendMessage msg = captor.getValue();
        assertAll(
                () -> assertEquals("456", msg.getChatId()),
                () -> assertEquals("Выберите действие:", msg.getText()),
                () -> assertEquals("Markdown", msg.getParseMode()),
                () -> assertEquals(keyboard, msg.getReplyMarkup())
        );
    }

    // ========= sendTextWithInlineKeyboard =========

    @Test
    void sendTextWithInlineKeyboard_callsExecuteWithInlineKeyboard() throws TelegramApiException {
        // Given
        Long chatId = 789L;
        String text = "Нажмите кнопку:";
        InlineKeyboardMarkup inlineKeyboard = InlineKeyboardMarkup.builder().build();

        // When
        messageSender.sendTextWithInlineKeyboard(chatId, text, inlineKeyboard);

        // Then
        var captor = forClass(SendMessage.class);
        verify(mockChatBot).execute(captor.capture());

        SendMessage msg = captor.getValue();
        assertAll(
                () -> assertEquals("789", msg.getChatId()),
                () -> assertEquals("Нажмите кнопку:", msg.getText()),
                () -> assertEquals("Markdown", msg.getParseMode()),
                () -> assertEquals(inlineKeyboard, msg.getReplyMarkup())
        );
    }

    // ========= editMessageText =========

    @Test
    void editMessageText_callsExecuteWithCorrectEditMessageText() throws TelegramApiException {
        // Given
        Long chatId = 100L;
        Integer messageId = 5;
        String text = "Обновлённый текст";
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().build();

        // When
        messageSender.editMessageText(chatId, messageId, text, keyboard);

        // Then
        var captor = forClass(EditMessageText.class);
        verify(mockChatBot).execute(captor.capture());

        EditMessageText editMsg = captor.getValue();
        assertAll(
                () -> assertEquals("100", editMsg.getChatId()),
                () -> assertEquals(5, editMsg.getMessageId()),
                () -> assertEquals("Обновлённый текст", editMsg.getText()),
                () -> assertEquals("Markdown", editMsg.getParseMode()),
                () -> assertEquals(keyboard, editMsg.getReplyMarkup())
        );
    }

    @Test
    void editMessageText_handlesNullKeyboard() throws TelegramApiException {
        // Given
        // When
        messageSender.editMessageText(200L, 10, "Текст без клавиатуры", null);

        // Then
        var captor = forClass(EditMessageText.class);
        verify(mockChatBot).execute(captor.capture());

        EditMessageText editMsg = captor.getValue();
        assertNull(editMsg.getReplyMarkup());
    }
}
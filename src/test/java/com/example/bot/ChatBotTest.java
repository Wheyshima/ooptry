
package com.example.bot;

import com.example.bot.command.Command;
import com.example.bot.command.CommandRegistry;
import com.example.bot.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


// Тесты для основного класса бота

class ChatBotTest {

    private ChatBot chatBot;
    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() {
        // Создаем мок DatabaseManager
        databaseManager = mock(DatabaseManager.class);
        // Создаем реальный бот с тестовыми данными
        chatBot = new ChatBot("test_bot", "test_token", databaseManager);
    }

    @Test
    void testBotCreation() {
        // Given & When
        ChatBot bot = new ChatBot("test_bot", "test_token",databaseManager);

        // Then
        assertNotNull(bot);
        assertEquals("test_bot", bot.getBotUsername());
        assertEquals("test_token", bot.getBotToken());
    }

    @Test
    void testHandleMessageWithText() {
        // Given
        Message message = createMessageWithText("/start");
        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        // Если метод выполнился без исключений - тест пройден
        assertTrue(true);
    }

    @Test
    void testHandleMessageWithoutText() {
        // Given
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(false);

        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        // Метод должен выполниться без исключений для сообщения без текста
        assertTrue(true);
    }

    @Test
    void testHandleUpdateWithoutMessage() {
        // Given
        Update update = new Update();
        // message не установлен

        // When
        chatBot.onUpdateReceived(update);

        // Then
        // Метод должен обработать update без message
        assertTrue(true);
    }

    @Test
    void testBotInfo() {
        // Given & When
        String username = chatBot.getBotUsername();
        String token = chatBot.getBotToken();

        // Then
        assertEquals("test_bot", username);
        assertEquals("test_token", token);
    }

    @Test
    void testBotInitialization() {
        // Given & When
        ChatBot bot = new ChatBot("my_bot", "my_token",databaseManager);

        // Then
        assertNotNull(bot);
        // Бот должен быть готов к работе после создания
        assertDoesNotThrow(() -> bot.onUpdateReceived(new Update()));
    }

    @Test
    void testMultipleMessagesHandling() {
        // Given
        Message message1 = createMessageWithText("/start");
        Message message2 = createMessageWithText("/about");
        Message message3 = createMessageWithText("/help");

        Update update1 = new Update();
        update1.setMessage(message1);

        Update update2 = new Update();
        update2.setMessage(message2);

        Update update3 = new Update();
        update3.setMessage(message3);

        // When & Then
        assertDoesNotThrow(() -> {
            chatBot.onUpdateReceived(update1);
            chatBot.onUpdateReceived(update2);
            chatBot.onUpdateReceived(update3);
        });
    }

    @Test
    void testEmptyMessageText() {
        // Given
        Message message = createMessageWithText("");
        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        // Должен обработать пустой текст без ошибок
        assertTrue(true);
    }

    @Test
    void testVeryLongMessage() {
        // Given
        String longText = "A".repeat(1000);
        Message message = createMessageWithText(longText);
        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        // Должен обработать длинное сообщение без ошибок
        assertTrue(true);
    }

    @Test
    void testSpecialCharactersInMessage() {
        // Given
        Message message = createMessageWithText("/start @#$%^&*() test");
        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        // Должен обработать специальные символы без ошибок
        assertTrue(true);
    }

    private Message createMessageWithText(String text) {
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(12345L);
        return message;
    }
}


package com.example.bot;

import com.example.bot.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatBotTest {
    private static final String TEST_WEATHER_API_KEY = "test_openweather_key_123";
    private ChatBot chatBot;
    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() {
        databaseManager = mock(DatabaseManager.class);
        chatBot = new ChatBot("test_bot", "test_token", databaseManager,TEST_WEATHER_API_KEY);
    }

    @Test
    void botCreation_setsCorrectUsernameAndToken() {
        assertEquals("test_bot", chatBot.getBotUsername());
        assertEquals("test_token", chatBot.getBotToken());
    }

    @Test
    void handleMessage_startCommand_savesUserToDatabase() {
        // Given
        Long userId = 12345L;
        String username = "testuser";

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUserName()).thenReturn(username);

        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(user);
        when(message.getText()).thenReturn("/start");
        when(message.hasText()).thenReturn(true);
        when(message.getChatId()).thenReturn(userId);

        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        verify(databaseManager).saveUser(userId, username);
    }

    @Test
    void handleMessage_unknownCommand_savesUserButDoesNotCrash() {
        // Given
        Long userId = 12345L;
        String username = "testuser";

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUserName()).thenReturn(username);

        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(user);
        when(message.getText()).thenReturn("/unknown_command");
        when(message.hasText()).thenReturn(true);
        when(message.getChatId()).thenReturn(userId);

        Update update = new Update();
        update.setMessage(message);

        // When & Then — не должно быть исключений
        assertDoesNotThrow(() -> chatBot.onUpdateReceived(update));
        verify(databaseManager).saveUser(userId, username);
    }

    @Test
    void handleMessage_messageWithoutText_doesNotSaveUser() {
        // Given
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(false);

        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        verify(databaseManager, never()).saveUser(anyLong(), anyString());
    }

    @Test
    void handleMessage_updateWithoutMessage_doesNothing() {
        // Given
        Update update = new Update(); // message == null

        // When & Then
        assertDoesNotThrow(() -> chatBot.onUpdateReceived(update));
        verify(databaseManager, never()).saveUser(anyLong(), anyString());
    }

    @Test
    void handleMessage_emptyText_savesUser() {
        // Given
        Long userId = 12345L;
        String username = "testuser";

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUserName()).thenReturn(username);

        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(user);
        when(message.getText()).thenReturn("");
        when(message.hasText()).thenReturn(true);
        when(message.getChatId()).thenReturn(userId);

        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        // Пустой текст — всё равно сохраняем пользователя (логика бота такова)
        verify(databaseManager).saveUser(userId, username);
    }

    @Test
    void handleMessage_specialCharacters_savesUser() {
        // Given
        Long userId = 12345L;
        String username = "testuser";

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUserName()).thenReturn(username);

        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(user);
        when(message.getText()).thenReturn("/start @#$%^&*() тест");
        when(message.hasText()).thenReturn(true);
        when(message.getChatId()).thenReturn(userId);

        Update update = new Update();
        update.setMessage(message);

        // When
        chatBot.onUpdateReceived(update);

        // Then
        verify(databaseManager).saveUser(userId, username);
    }

    @Test
    void multipleMessages_saveEachUser() {
        // Given
        Update update1 = createUpdateWithText("/start", 123L, "user1");
        Update update2 = createUpdateWithText("/help", 456L, "user2");
        Update update3 = createUpdateWithText("/unknown", 789L, "user3");

        // When
        chatBot.onUpdateReceived(update1);
        chatBot.onUpdateReceived(update2);
        chatBot.onUpdateReceived(update3);

        // Then
        verify(databaseManager).saveUser(123L, "user1");
        verify(databaseManager).saveUser(456L, "user2");
        verify(databaseManager).saveUser(789L, "user3");
    }

    // Вспомогательный метод для создания Update
    private Update createUpdateWithText(String text, Long userId, String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUserName()).thenReturn(username);

        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(user);
        when(message.getText()).thenReturn(text);
        when(message.hasText()).thenReturn(true);
        when(message.getChatId()).thenReturn(userId);

        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
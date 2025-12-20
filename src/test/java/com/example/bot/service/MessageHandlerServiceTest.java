// com.example.bot.service/MessageHandlerServiceTest.java
package com.example.bot.service;

import com.example.bot.command.Command;
import com.example.bot.command.CommandRegistry;
import com.example.bot.database.DatabaseManager;
import com.example.bot.keyboard.InlineKeyboardFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageHandlerServiceTest {

    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private CommandRegistry mockCommandRegistry;
    @Mock
    private MessageSender mockMessageSender;
    @Mock
    private UserStateService mockUserStateService;
    @Mock
    private Command mockCommand;

    private MessageHandlerService messageHandlerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        messageHandlerService = new MessageHandlerService(
                mockDatabaseManager,
                mockCommandRegistry,
                mockMessageSender,
                mockUserStateService
        );
    }

    // ========= Вспомогательный метод для создания Message =========
    private Message createMessage(Long userId, Long chatId, String text) {
        Message message = new Message();
        User user = new User();
        user.setId(userId);
        user.setUserName("testuser");
        message.setFrom(user);
        Chat chat = new Chat();
        chat.setId(chatId);
        message.setChat(chat);
        message.setText(text);
        return message;
    }

    // ========= Тест: активное состояние (не таймаут) =========
    @Test
    void handleMessage_hasActiveState_handlesState() {
        // Given
        Long userId = 123L;
        Long chatId = 123L;
        String text = "новый текст";
        Message message = createMessage(userId, chatId, text);

        when(mockUserStateService.hasActiveState(userId)).thenReturn(true);
        when(mockUserStateService.isEditTimedOut(userId)).thenReturn(false);

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockUserStateService).handleUserState(eq(userId), eq(text), eq(chatId));
        verify(mockDatabaseManager).saveUser(eq(userId), eq("testuser"));
        verifyNoInteractions(mockCommandRegistry, mockMessageSender); // команда не вызывается
    }

    // ========= Тест: состояние с таймаутом =========
    @Test
    void handleMessage_hasActiveStateButTimedOut_cleansUpAndSendsTimeoutMessage() {
        // Given
        Long userId = 123L;
        Long chatId = 123L;
        Message message = createMessage(userId, chatId, "текст");

        when(mockUserStateService.hasActiveState(userId)).thenReturn(true);
        when(mockUserStateService.isEditTimedOut(userId)).thenReturn(true);

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockUserStateService).cleanupEditState(userId);
        verify(mockMessageSender).sendText(eq(chatId), contains("⏰ *Время редактирования истекло*"));
        verify(mockDatabaseManager).saveUser(eq(userId), eq("testuser"));
    }

    // ========= Тест: команда /start — отправка reply-клавиатуры =========
    @Test
    void handleMessage_startCommand_sendsReplyKeyboard() {
        // Given
        Message message = createMessage(123L, 123L, "/start");
        String response = "Добро пожаловать!";
        when(mockCommandRegistry.findCommandForMessage(message)).thenReturn(mockCommand);
        when(mockCommand.execute(message)).thenReturn(response);

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockMessageSender).sendTextWithKeyboard(
                eq(123L),
                eq(response),
                eq(KeyboardService.mainMenu())
        );
    }

    // ========= Тест: команда /todo — отправка inline-клавиатуры =========
    @Test
    void handleMessage_todoCommand_sendsInlineKeyboard() {
        // Given
        Message message = createMessage(456L, 456L, "/todo");
        String response = "📋 Ваши задачи...";
        when(mockCommandRegistry.findCommandForMessage(message)).thenReturn(mockCommand);
        when(mockCommand.execute(message)).thenReturn(response);
        // Мокаем, что задач нет
        when(mockDatabaseManager.getWishes(456L)).thenReturn(java.util.List.of());
        when(mockDatabaseManager.isWishlistLocked(456L)).thenReturn(false);

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockMessageSender).sendTextWithInlineKeyboard(
                eq(456L),
                eq(response),
                eq(InlineKeyboardFactory.getTodoActionsKeyboard())
        );
    }

    // ========= Тест: команда /wishlist — отправка inline-клавиатуры с параметрами =========
    @Test
    void handleMessage_wishlistCommand_sendsInlineKeyboardWithParams() {
        // Given
        Message message = createMessage(789L, 789L, "/wishlist");
        String response = "🌟 Карта желаний";
        when(mockCommandRegistry.findCommandForMessage(message)).thenReturn(mockCommand);
        when(mockCommand.execute(message)).thenReturn(response);
        when(mockDatabaseManager.isWishlistLocked(789L)).thenReturn(true);
        when(mockDatabaseManager.getWishes(789L)).thenReturn(java.util.List.of()); // пусто

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockMessageSender).sendTextWithInlineKeyboard(
                eq(789L),
                eq(response),
                eq(InlineKeyboardFactory.getWishlistActionsKeyboard(true, false))
        );
    }

    // ========= Тест: ошибка при выполнении команды =========
    @Test
    void handleMessage_commandExecutionFails_sendsErrorMessage() {
        // Given
        Message message = createMessage(100L, 100L, "/todo");
        when(mockCommandRegistry.findCommandForMessage(message)).thenReturn(mockCommand);
        when(mockCommand.execute(message)).thenThrow(new RuntimeException("Ошибка!"));

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockMessageSender).sendText(eq(100L), eq("Произошла ошибка при обработке команды."));
    }

    // ========= Тест: неизвестная команда =========
    @Test
    void handleMessage_unknownCommand_sendsHelpMessage() {
        // Given
        Message message = createMessage(200L, 200L, "/unknown");

        when(mockCommandRegistry.findCommandForMessage(message)).thenReturn(null);

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockMessageSender).sendTextWithKeyboard(
                eq(200L),
                eq("Неизвестная команда. Используйте /help или выберите действие из меню."),
                eq(KeyboardService.mainMenu())
        );
    }

    // ========= Тест: команда /setcity с установленным городом =========
    @Test
    void handleMessage_setCityWithExistingCity_sendsInlineKeyboard() {
        // Given
        Message message = createMessage(300L, 300L, "/setcity");
        String response = "Текущий город: Москва";
        when(mockCommandRegistry.findCommandForMessage(message)).thenReturn(mockCommand);
        when(mockCommand.execute(message)).thenReturn(response);
        when(mockDatabaseManager.getUserCity(300L)).thenReturn("Москва");

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockMessageSender).sendTextWithInlineKeyboard(
                eq(300L),
                eq(response),
                eq(InlineKeyboardFactory.getChangeCityConfirmationKeyboard())
        );
    }

    // ========= Тест: команда /setcity без установленного города =========
    @Test
    void handleMessage_setCityWithoutExistingCity_sendsTextOnly() {
        // Given
        Message message = createMessage(400L, 400L, "/setcity");
        String response = "Город не установлен.";
        when(mockCommandRegistry.findCommandForMessage(message)).thenReturn(mockCommand);
        when(mockCommand.execute(message)).thenReturn(response);
        when(mockDatabaseManager.getUserCity(400L)).thenReturn(null);

        // When
        messageHandlerService.handleMessage(message);

        // Then
        verify(mockMessageSender).sendText(eq(400L), eq(response));
    }
}
package com.example.bot.service;

import com.example.bot.command.CommandRegistry;
import com.example.bot.command.impl.TodoCommand;
import com.example.bot.command.impl.WishlistCommand;
import com.example.bot.database.DatabaseManager;
import com.example.bot.model.City;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserStateServiceTest {

    @Mock
    private CityService mockCityService;
    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private MessageSender mockMessageSender;
    @Mock
    private CommandRegistry mockCommandRegistry;

    // УБРАЛИ: @Mock private TodoCommand mockTodoCommand;

    private UserStateService userStateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userStateService = new UserStateService(
                mockCityService,
                mockDatabaseManager,
                mockMessageSender,
                mockCommandRegistry
        );
    }

    @AfterEach
    void tearDown() {
        userStateService.shutdown();
    }

    @Test
    void startTodoEditState_storesStateAndStartTime() {
        Long userId = 123L;
        int taskId = 5;

        userStateService.startTodoEditState(userId, taskId);

        assertTrue(userStateService.hasActiveState(userId));
        assertFalse(userStateService.isEditTimedOut(userId));
    }

    @Test
    void startCitySelectionState_storesStateAndStartTime() {
        Long userId = 456L;

        userStateService.startCitySelectionState(userId);

        assertTrue(userStateService.hasActiveState(userId));
        assertFalse(userStateService.isEditTimedOut(userId));
    }

    @Test
    void cancelUserState_removesState() {
        Long userId = 123L;
        userStateService.startTodoEditState(userId, 1);

        assertTrue(userStateService.hasActiveState(userId));

        userStateService.cancelUserState(userId);

        assertFalse(userStateService.hasActiveState(userId));
    }

    @Test
    void handleUserState_cancelCommand_cleansUpAndSendsMessage() {
        Long userId = 123L;
        Long chatId = 123L;
        userStateService.startTodoEditState(userId, 1);

        userStateService.handleUserState(userId, "отмена", chatId);

        assertFalse(userStateService.hasActiveState(userId));
        verify(mockMessageSender).sendText(eq(chatId), eq("❌ Действие отменено."));
    }

    // ✅ ИСПРАВЛЕН: используем commandRegistry
    @Test
    void handleUserState_editingTodoTask_callsTodoCommand() {
        Long userId = 123L;
        Long chatId = 123L;
        int taskId = 7;

        TodoCommand mockTodoCmd = mock(TodoCommand.class);
        when(mockCommandRegistry.getCommand("todo")).thenReturn(mockTodoCmd);
        when(mockTodoCmd.handleEditInput(userId, taskId, "Новый текст задачи"))
                .thenReturn("✅ Задача обновлена!");

        userStateService.startTodoEditState(userId, taskId);
        userStateService.handleUserState(userId, "Новый текст задачи", chatId);

        verify(mockTodoCmd).handleEditInput(userId, taskId, "Новый текст задачи");
        verify(mockMessageSender).sendText(eq(chatId), eq("✅ Задача обновлена!"));
        assertFalse(userStateService.hasActiveState(userId));
    }

    @Test
    void handleUserState_settingCity_validCity_updatesAndSendsSuccess() {
        Long userId = 456L;
        Long chatId = 456L;
        City matchedCity = new City("Москва", "Москва", 12_600_000L);

        userStateService.startCitySelectionState(userId);
        when(mockCityService.findCity("Москва")).thenReturn(matchedCity);

        userStateService.handleUserState(userId, "Москва", chatId);

        verify(mockDatabaseManager).updateUserCity(userId, "Москва");
        verify(mockMessageSender).sendText(eq(chatId), contains("✅ Город установлен: *Москва*"));
        assertFalse(userStateService.hasActiveState(userId));
    }

    @Test
    void handleUserState_settingCity_invalidCity_sendsError() {
        Long userId = 456L;
        Long chatId = 456L;

        userStateService.startCitySelectionState(userId);
        when(mockCityService.findCity("НесуществующийГород")).thenReturn(null);

        userStateService.handleUserState(userId, "НесуществущийГород", chatId);

        verify(mockDatabaseManager, never()).updateUserCity(anyLong(), any());
        verify(mockMessageSender).sendText(eq(chatId), contains("❌ Город не найден"));
        assertTrue(userStateService.hasActiveState(userId)); // состояние остаётся
    }

    // ✅ ИСПРАВЛЕН: используем commandRegistry
    @Test
    void handleUserState_addingTodoTask_callsTodoCommand() {
        Long userId = 100L;
        Long chatId = 100L;

        TodoCommand mockTodoCmd = mock(TodoCommand.class);
        when(mockCommandRegistry.getCommand("todo")).thenReturn(mockTodoCmd);
        when(mockTodoCmd.handleAddTask(userId, "Купить хлеб"))
                .thenReturn("✅ Задача добавлена!");

        userStateService.startTodoAddState(userId);
        userStateService.handleUserState(userId, "Купить хлеб", chatId);

        verify(mockTodoCmd).handleAddTask(userId, "Купить хлеб");
        verify(mockMessageSender).sendText(eq(chatId), eq("✅ Задача добавлена!"));
        assertFalse(userStateService.hasActiveState(userId));
    }

    @Test
    void handleUserState_addingWishlistItem_callsWishlistCommand() {
        Long userId = 200L;
        Long chatId = 200L;
        WishlistCommand mockWishlistCmd = mock(WishlistCommand.class);
        when(mockCommandRegistry.getCommand("wishlist")).thenReturn(mockWishlistCmd);
        when(mockWishlistCmd.handleAddWish(userId, "Путешествие на Бали"))
                .thenReturn("✨ Желание добавлено!");

        userStateService.startWishlistAddState(userId);
        userStateService.handleUserState(userId, "Путешествие на Бали", chatId);

        verify(mockWishlistCmd).handleAddWish(userId, "Путешествие на Бали");
        verify(mockMessageSender).sendText(eq(chatId), eq("✨ Желание добавлено!"));
        assertFalse(userStateService.hasActiveState(userId));
    }
}
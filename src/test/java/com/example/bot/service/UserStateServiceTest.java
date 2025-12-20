package com.example.bot.service;

import com.example.bot.command.impl.TodoCommand;
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
    private TodoCommand mockTodoCommand;

    private UserStateService userStateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userStateService = new UserStateService(mockCityService, mockDatabaseManager, mockMessageSender);
        userStateService.setTodoCommand(mockTodoCommand);
    }

    @AfterEach
    void tearDown() {
        userStateService.shutdown(); // останавливаем scheduler
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
    void isEditTimedOut_returnsTrue_afterTimeout() throws InterruptedException {
        Long userId = 123L;
        userStateService.startTodoEditState(userId, 1);

        // Эмулируем прохождение времени
        Thread.sleep(11_000); // чуть больше 10 сек

        assertTrue(userStateService.isEditTimedOut(userId));
    }

    @Test
    void handleUserState_cancelCommand_cleansUpAndSendsMessage() {
        Long userId = 123L;
        Long chatId = 123L;
        userStateService.startTodoEditState(userId, 1);

        userStateService.handleUserState(userId, "отмена", chatId);

        assertFalse(userStateService.hasActiveState(userId));
        verify(mockMessageSender).sendText(eq(chatId), eq(" Действие отменено."));
    }

    @Test
    void handleUserState_editingTodoTask_callsTodoCommand() {
        Long userId = 123L;
        Long chatId = 123L;
        int taskId = 7;

        userStateService.startTodoEditState(userId, taskId);
        when(mockTodoCommand.handleEditInput(userId, taskId, "Новый текст задачи"))
                .thenReturn("✅ Задача обновлена!");

        userStateService.handleUserState(userId, "Новый текст задачи", chatId);

        verify(mockTodoCommand).handleEditInput(userId, taskId, "Новый текст задачи");
        verify(mockMessageSender).sendText(eq(chatId), eq("✅ Задача обновлена!"));
        assertFalse(userStateService.hasActiveState(userId)); // состояние очищено
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

        userStateService.handleUserState(userId, "НесуществующийГород", chatId);

        verify(mockDatabaseManager, never()).updateUserCity(anyLong(), any());
        verify(mockMessageSender).sendText(eq(chatId), contains("❌ Город не найден"));
        assertFalse(userStateService.hasActiveState(userId));
    }

    @Test
    void cleanupExpiredEditStates_removesTimedOutStatesAndNotifiesUser() throws InterruptedException {
        Long userId = 789L;

        // 1. Начинаем редактирование
        userStateService.startTodoEditState(userId, 1);

        // 2. Эмулируем таймаут: ждём 11 секунд
        Thread.sleep(11_000); // больше, чем EDIT_TIMEOUT_MS = 10_000

        // 3. Вызываем очистку (как это делает таймер)
        userStateService.cleanupExpiredEditStates();

        // 4. Проверяем, что состояние удалено и уведомление отправлено
        assertFalse(userStateService.hasActiveState(userId));
        verify(mockMessageSender).sendText(eq(userId), contains("⏰ *Время редактирования истекло*"));
    }
}
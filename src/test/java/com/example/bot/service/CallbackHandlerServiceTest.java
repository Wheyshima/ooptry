// com.example.bot.service/CallbackHandlerServiceTest.java
package com.example.bot.service;

import com.example.bot.command.Command;
import com.example.bot.command.CommandRegistry;
import com.example.bot.command.impl.TodoCommand;
import com.example.bot.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallbackHandlerServiceTest {

    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private CommandRegistry mockCommandRegistry;
    @Mock
    private MessageSender mockMessageSender;
    @Mock
    private UserStateService mockUserStateService;
    @Mock
    private CityService mockCityService;
    @Mock
    private TodoCommand mockTodoCommand;
    @Mock
    private Command mockCommand;

    private CallbackHandlerService callbackHandlerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        callbackHandlerService = new CallbackHandlerService(
                mockDatabaseManager,
                mockCommandRegistry,
                mockMessageSender,
                mockUserStateService,
                mockCityService
        );
    }

    // ========= Вспомогательный метод для создания CallbackQuery =========
    private CallbackQuery createCallbackQuery(Integer messageId, String data) {
        CallbackQuery callback = new CallbackQuery();
        User user = new User();
        user.setId(123L);
        callback.setFrom(user);
        Message message = new Message();
        Chat chat = new Chat();
        chat.setId(123L);
        message.setChat(chat);
        message.setMessageId(messageId);
        callback.setMessage(message);
        callback.setData(data);
        return callback;
    }

    // ========= Тест: выбор города "Да" =========
    @Test
    void handleCallback_changeCityYes_showsCitySelectionMenu() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "change_city_yes");
        when(mockCityService.getTop10Cities()).thenReturn(List.of("Москва", "Санкт-Петербург"));

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendTextWithInlineKeyboard(
                eq(123L),
                anyString(),
                any() // клавиатура
        );
    }

    // ========= Тест: выбор города "Нет" =========
    @Test
    void handleCallback_changeCityNo_sendsConfirmation() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "change_city_no");

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendText(eq(123L), eq("✅ Изменение отменено."));
    }

    // ========= Тест: выбор города из списка =========
    @Test
    void handleCallback_selectCity_executesSetCityCommand() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "select_city:Москва");
        when(mockCommandRegistry.findCommandForMessage(any(Message.class))).thenReturn(mockCommand);
        when(mockCommand.execute(any(Message.class))).thenReturn("Город установлен");

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendText(eq(123L), eq("Город установлен"));
    }

    // ========= Тест: ручной ввод города =========
    @Test
    void handleCallback_selectCityManual_startsCitySelectionState() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "select_city_manual");

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockUserStateService).startCitySelectionState(eq(123L));
        verify(mockMessageSender).sendText(eq(123L), contains("Введите название города"));
    }

    // ========= Тест: недельная статистика =========
    @Test
    void handleCallback_statsWeek_executesStatsWeekCommand() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "stats:week");
        when(mockCommandRegistry.findCommandForMessage(any(Message.class))).thenReturn(mockCommand);
        when(mockCommand.execute(any(Message.class))).thenReturn("📊 Статистика за неделю");

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendText(eq(123L), eq("📊 Статистика за неделю"));
    }

    // ========= Тест: добавление задачи =========
    @Test
    void handleCallback_todoAdd_startsTodoAddState() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "todo:add");

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockUserStateService).startTodoAddState(eq(123L));
        verify(mockMessageSender).sendText(eq(123L), contains("✍️ *Добавление задачи*"));
    }

    // ========= Тест: выбор задач для завершения (нет задач) =========
    @Test
    void handleCallback_todoComplete_noTasks_sendsEmptyMessage() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "todo:complete");
        when(mockDatabaseManager.getDailyTasks(123L)).thenReturn(Collections.emptyList());

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendText(eq(123L), eq("📭 У вас нет задач на сегодня."));
    }

    // ========= Тест: выбор задач для редактирования (есть задачи) =========
    @Test
    void handleCallback_todoEdit_withTasks_showsSelectionMenu() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "todo:edit");
        var tasks = List.of(
                new com.example.bot.database.DatabaseManager.Task(1, "Задача 1", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getDailyTasks(123L)).thenReturn(tasks);

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendTextWithInlineKeyboard(
                eq(123L),
                contains("✏️ *Выберите задачу для редактирования:*"),
                any()
        );
    }

    // ========= Тест: обновление списка задач =========
    @Test
    void handleCallback_todoRefresh_editsMessage() {
        // Given
        CallbackQuery callback = createCallbackQuery(5, "todo:refresh");
        when(mockCommandRegistry.findCommandForMessage(any(Message.class))).thenReturn(mockCommand);
        when(mockCommand.execute(any(Message.class))).thenReturn("📋 Обновлённый список");

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).editMessageText(
                eq(123L),
                eq(5),
                eq("📋 Обновлённый список"),
                any() // клавиатура
        );
    }

    // ========= Тест: выполнение действия с задачей =========
    @Test
    void handleCallback_todoAction_validTask_executesCommand() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "todo:edit:1");
        when(mockDatabaseManager.getDailyTasks(123L)).thenReturn(List.of(
                new com.example.bot.database.DatabaseManager.Task(1, "Задача", false, LocalDateTime.now())
        ));
        when(mockCommandRegistry.findCommandForMessage(any(Message.class))).thenReturn(mockCommand);
        when(mockCommand.execute(any(Message.class))).thenReturn("Задача обновлена");

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendText(eq(123L), eq("Задача обновлена"));
    }

    // ========= Тест: добавление желания (разблокировано) =========
    @Test
    void handleCallback_wishlistAdd_notLocked_startsAddState() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "wishlist:add");
        when(mockDatabaseManager.isWishlistLocked(123L)).thenReturn(false);

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockUserStateService).startWishlistAddState(eq(123L));
        verify(mockMessageSender).sendText(eq(123L), contains("✨ *Добавление желания*"));
    }

    // ========= Тест: добавление желания (заблокировано) =========
    @Test
    void handleCallback_wishlistAdd_locked_sendsErrorMessage() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "wishlist:add");
        when(mockDatabaseManager.isWishlistLocked(123L)).thenReturn(true);

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendText(eq(123L), eq("🔒 Добавление желаний временно заблокировано."));
    }

    // ========= Тест: ошибка в обработке =========
    @Test
    void handleCallback_exceptionInCityService_sendsErrorMessage() {
        // Given
        CallbackQuery callback = createCallbackQuery(1, "change_city_yes");

        // Заставим cityService.getTop10Cities() выбросить исключение
        when(mockCityService.getTop10Cities()).thenThrow(new RuntimeException("DB error"));

        // When
        callbackHandlerService.handleCallback(callback);

        // Then
        verify(mockMessageSender).sendText(eq(123L), eq("Произошла ошибка. Попробуйте снова."));
    }
}
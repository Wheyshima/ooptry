package com.example.bot.command.impl;

import com.example.bot.ChatBot;
import com.example.bot.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TodoCommandTest {

    private DatabaseManager mockDatabaseManager;
    private ChatBot mockChatBot;
    private TodoCommand todoCommand;
    private Message mockMessage;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockDatabaseManager = Mockito.mock(DatabaseManager.class);
        mockChatBot = Mockito.mock(ChatBot.class);
        todoCommand = new TodoCommand(mockDatabaseManager, mockChatBot);

        mockMessage = Mockito.mock(Message.class);
        mockUser = Mockito.mock(User.class);

        when(mockMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(12345L);
        when(mockChatBot.hasActiveState(12345L)).thenReturn(false);
    }

    // ============ Тесты для /todo (показ задач) ============

    @Test
    void execute_emptyCommand_showsTasksList() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo");
        List<DatabaseManager.Task> tasks = Arrays.asList(
                new DatabaseManager.Task(1, "Задача 1", true, LocalDateTime.now()),
                new DatabaseManager.Task(2, "Задача 2", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(tasks);
        when(mockDatabaseManager.getDailyCompletionRate(12345L)).thenReturn(50.0);

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("*📋 Ваши задачи на сегодня:*"));
        assertTrue(result.contains("✅ [#1] Задача 1"));
        assertTrue(result.contains("⏳ [#2] Задача 2"));
        assertTrue(result.contains("📊 *Прогресс: 1/2 задач (50,0%)*"));
    }

    @Test
    void execute_emptyCommand_noTasks_showsEmptyMessage() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo");
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(Collections.emptyList());

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("📭 На сегодня задач нет. Добавьте новую:"));
        assertTrue(result.contains("`/todo add <ваша задача>`"));
    }

    // ============ Тесты для /todo add ============

    @Test
    void execute_addCommand_validTask_addsTaskSuccessfully() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo add Новая задача");
        when(mockDatabaseManager.addDailyTask(12345L, "Новая задача")).thenReturn(1);
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(
                Collections.singletonList(new DatabaseManager.Task(1, "Новая задача", false, LocalDateTime.now()))
        );
        when(mockDatabaseManager.getDailyCompletionRate(12345L)).thenReturn(0.0);

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("✅ *Задача добавлена!*"));
        assertTrue(result.contains("📝 Текст: Новая задача"));
        assertTrue(result.contains("• Всего задач: 1"));
    }

    @Test
    void execute_addCommand_emptyText_showsError() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo add ");

        // When
        String result = todoCommand.execute(mockMessage);
        // Then
        assertTrue(result.contains("⏰ Задачи автоматически удаляются в 00:00"));
    }

    @Test
    void execute_addCommand_tooShortText_showsError() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo add A");

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("❌ Текст задачи слишком короткий (минимум 2 символа)"));
    }

    @Test
    void execute_addCommand_tooLongText_showsError() {
        // Given
        String longText = "A".repeat(51);
        when(mockMessage.getText()).thenReturn("/todo add " + longText);

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("❌ Текст задачи слишком длинный (максимум 50 символов)"));
    }

    // ============ Тесты для /todo complete ============

    @Test
    void execute_completeCommand_validIndex_completesTask() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo complete 1");
        List<DatabaseManager.Task> tasks = Collections.singletonList(
                new DatabaseManager.Task(10, "Задача", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(tasks);
        when(mockDatabaseManager.completeDailyTask(12345L, 10)).thenReturn(true);
        when(mockDatabaseManager.getDailyCompletionRate(12345L)).thenReturn(100.0);

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("✅ *Задача завершена!*"));
        assertTrue(result.contains("📊 Общий прогресс: 100,0%"));
    }

    @Test
    void execute_completeCommand_invalidIndex_showsError() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo complete 5");
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(
                Collections.singletonList(new DatabaseManager.Task(1, "Задача", false, LocalDateTime.now()))
        );

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("❌ Неверный номер задачи. У вас всего 1 задач."));
    }

    @Test
    void execute_completeCommand_invalidFormat_showsError() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo complete abc");

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("❌ Неверный формат. Используйте: `/todo complete <номер>`"));
    }

    // ============ Тесты для /todo edit ============

    @Test
    void execute_editCommand_validIndex_startsEditMode() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo edit 1");
        List<DatabaseManager.Task> tasks = Collections.singletonList(
                new DatabaseManager.Task(10, "Старый текст", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(tasks);

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("✏️ *Редактирование задачи #1*"));
        assertTrue(result.contains("📝 *Текущий текст:* Старый текст"));
        verify(mockChatBot).startTodoEditState(12345L, 10);
    }

    @Test
    void execute_editCommand_completedTask_showsError() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo edit 1");
        List<DatabaseManager.Task> tasks = Collections.singletonList(
                new DatabaseManager.Task(10, "Задача", true, LocalDateTime.now())
        );
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(tasks);

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("⚠️ Нельзя редактировать завершенную задачу #1"));
    }

    // ============ Тесты для /todo stats ============

    @Test
    void execute_statsCommand_showsStatistics() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo stats");
        List<DatabaseManager.Task> tasks = Arrays.asList(
                new DatabaseManager.Task(1, "Задача 1", true, LocalDateTime.now()),
                new DatabaseManager.Task(2, "Задача 2", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(tasks);
        when(mockDatabaseManager.getDailyCompletionRate(12345L)).thenReturn(50.0);

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("📊 *Статистика задач:*"));
        assertTrue(result.contains("• Всего задач: 2"));
        assertTrue(result.contains("• Выполнено: 1"));
        assertTrue(result.contains("• Прогресс: 50,0%"));
    }

    // ============ Тесты для неверных команд ============

    @Test
    void execute_invalidCommand_showsUsage() {
        // Given
        when(mockMessage.getText()).thenReturn("/todo invalid");

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("🎯 *Управление задачами:*"));
        assertTrue(result.contains("`/todo add <текст>`"));
    }

    // ============ Тесты описания команды ============

    @Test
    void commandNameAndDescriptionShouldBeCorrect() {
        assertEquals("todo", todoCommand.getBotCommand().getCommand());
        assertEquals("Управление ежедневными задачами", todoCommand.getDescription());
    }

    // ============ Тесты на отмену активного состояния ============

    @Test
    void execute_withActiveState_cancelsPreviousAction() {
        // Given
        when(mockChatBot.hasActiveState(12345L)).thenReturn(true);
        when(mockMessage.getText()).thenReturn("/todo");

        // When
        String result = todoCommand.execute(mockMessage);

        // Then
        assertTrue(result.contains("⚠️ Предыдущее действие отменено. Обрабатываю новую команду..."));
        verify(mockChatBot).cancelUserState(12345L);
    }
}
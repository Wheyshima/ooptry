package com.example.bot.command.impl;

import com.example.bot.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatsCommandTest {

    private DatabaseManager mockDatabaseManager;
    private StatsCommand statsCommand;
    private Message mockMessage;

    @BeforeEach
    void setUp() {
        // Инициализация моков и команды перед каждым тестом
        mockDatabaseManager = Mockito.mock(DatabaseManager.class);
        statsCommand = new StatsCommand(mockDatabaseManager);
        mockMessage = Mockito.mock(Message.class);
        User mockUser = Mockito.mock(User.class);

        when(mockMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(12345L);
    }

    // ============ Тесты для /stats (сегодняшняя статистика) ============

    @Test
    void execute_statsCommand_withTasks_showsCurrentProgress() {
        // Проверяет отображение текущего прогресса, когда у пользователя есть задачи
        // Ожидается: показ количества выполненных/всего задач и процент продуктивности
        when(mockMessage.getText()).thenReturn("/stats");

        List<DatabaseManager.Task> tasks = Arrays.asList(
                new DatabaseManager.Task(1, "Task 1", true, LocalDateTime.now()),
                new DatabaseManager.Task(2, "Task 2", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(tasks);
        when(mockDatabaseManager.getDailyCompletionRate(12345L)).thenReturn(50.0);
        when(mockDatabaseManager.getTodayStats(12345L)).thenReturn(null);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn(null);

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("✅ *Выполнено:* 1/2 задач"));
        assertTrue(result.contains("📈 *Продуктивность:* 50,0%"));
        assertFalse(result.contains("💾 *Сохраненная:*")); // Сохранённой статистики нет
        assertTrue(result.contains("Статистика за сегодня"));
    }

    @Test
    void execute_statsCommand_noTasksButSavedStats_showsSavedProgress() {
        // Проверяет отображение сохранённой статистики, когда задачи уже удалены (после очистки),
        // но статистика за день сохранена
        when(mockMessage.getText()).thenReturn("/stats");

        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(Collections.emptyList());
        when(mockDatabaseManager.getTodayStats(12345L)).thenReturn(75.5);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn("Moscow");

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("🏙️ *Город:* Moscow"));
        assertTrue(result.contains("✅ *Выполнено:* 0/0 задач"));
        assertTrue(result.contains("📈 *Сохраненная продуктивность:* 75,5%"));
        assertTrue(result.contains("💾 *Сохраненная:* 75,5%"));
    }

    @Test
    void execute_statsCommand_noTasksNoSavedStats_showsZeroProgress() {
        // Проверяет отображение статистики, когда нет ни задач, ни сохранённых данных
        // Ожидается: прогресс 0% без упоминания сохранённой статистики
        when(mockMessage.getText()).thenReturn("/stats");

        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(Collections.emptyList());
        when(mockDatabaseManager.getTodayStats(12345L)).thenReturn(null);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn(null);

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("✅ *Выполнено:* 0/0 задач"));
        assertTrue(result.contains("📈 *Продуктивность:* 0,0%"));
        assertFalse(result.contains("💾 *Сохраненная:*"));
    }

    // ============ Тесты для /stats week (недельная статистика) ============

    @Test
    void execute_statsWeekCommand_withData_showsDetailedStats() {
        // Проверяет полную недельную статистику с данными за 2 дня (Пн и Вт)
        // Ожидается: средняя продуктивность, детализация по дням, диапазон недели
        when(mockMessage.getText()).thenReturn("/stats week");

        DatabaseManager.ProductivityStat monday = new DatabaseManager.ProductivityStat(
                100.0,
                LocalDate.of(2025, 6, 2),
                LocalDateTime.now(),
                2,
                2
        );
        DatabaseManager.ProductivityStat tuesday = new DatabaseManager.ProductivityStat(
                50.0,
                LocalDate.of(2025, 6, 3),
                LocalDateTime.now(),
                4,
                2
        );
        List<DatabaseManager.ProductivityStat> weeklyStats = Arrays.asList(monday, tuesday);

        when(mockDatabaseManager.getWeeklyProductivityStats(12345L)).thenReturn(weeklyStats);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn(null);

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("*📊 Статистика за неделю:*"));
        assertTrue(result.contains("📅 *Активных дней:* 2/7"));
        assertTrue(result.contains("📈 *Средняя продуктивность:* 75,0%"));
        assertTrue(result.contains("🟢 *Понедельник* (100,0%)"));
        assertTrue(result.contains("🟠 *Вторник* (50,0%)"));
        assertTrue(result.contains("📝 Задач: 2/2 выполнено"));
        assertTrue(result.contains("📝 Задач: 2/4 выполнено"));
        assertTrue(result.contains("🗓️ *Неделя: 2025-06-02 – 2025-06-08*"));
    }

    @Test
    void execute_statsWeekCommand_noData_showsEmptyMessage() {
        // Проверяет сообщение, когда за текущую неделю нет данных о продуктивности
        // Ожидается: информативное сообщение с советом
        when(mockMessage.getText()).thenReturn("/stats week");
        when(mockDatabaseManager.getWeeklyProductivityStats(12345L)).thenReturn(Collections.emptyList());

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("Нет данных за текущую неделю"));
        assertTrue(result.contains("Добавьте задачи с помощью `/todo add`"));
    }

    @Test
    void execute_statsWeekCommand_withCity_showsCityInfo() {
        // Проверяет отображение города в недельной статистике, если он установлен
        when(mockMessage.getText()).thenReturn("/stats week");

        DatabaseManager.ProductivityStat stat = new DatabaseManager.ProductivityStat(
                100.0,
                LocalDate.of(2025, 6, 2),
                LocalDateTime.now(),
                1,
                1
        );
        when(mockDatabaseManager.getWeeklyProductivityStats(12345L)).thenReturn(List.of(stat));
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn("Екатеринбург");

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("🏙️ *Город:* Екатеринбург"));
    }

    // ============ Тесты для неверных аргументов ============

    @Test
    void execute_statsCommand_withInvalidArgument_showsHelp() {
        // Проверяет обработку неизвестного аргумента команды
        // Ожидается: сообщение об ошибке + краткая справка со статистикой за сегодня
        when(mockMessage.getText()).thenReturn("/stats abc");
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(Collections.emptyList());
        when(mockDatabaseManager.getTodayStats(12345L)).thenReturn(null);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn(null);

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("❓ *Неизвестный параметр:* 'abc'"));
        assertTrue(result.contains("Статистика за сегодня"));
    }

    // ============ Тесты описания команды ============

    @Test
    void commandNameAndDescriptionShouldBeCorrect() {
        // Проверяет корректность имени команды и её описания (для регистрации в Telegram)
        assertEquals("stats", statsCommand.getBotCommand().getCommand());
        assertEquals("Показать статистику выполнения", statsCommand.getDescription());
    }
}
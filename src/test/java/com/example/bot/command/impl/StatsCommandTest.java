package com.example.bot.command.impl;

import com.example.bot.database.DatabaseManager;
import com.example.bot.service.WeatherService;
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
    private WeatherService mockWeatherService; // ← добавлено
    private StatsCommand statsCommand;
    private Message mockMessage;

    @BeforeEach
    void setUp() {
        mockDatabaseManager = Mockito.mock(DatabaseManager.class);
        mockWeatherService = Mockito.mock(WeatherService.class); // ← создан мок
        statsCommand = new StatsCommand(mockDatabaseManager, mockWeatherService); // ← передано
        mockMessage = Mockito.mock(Message.class);
        User mockUser = Mockito.mock(User.class);

        when(mockMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(12345L);

        // По умолчанию: погода — заглушка (чтобы не падали тесты)
        when(mockWeatherService.getTodayForecast(anyString()))
                .thenReturn("🌤️ Облачно, +18°C");
    }

    // ============ Тесты для /stats (сегодняшняя статистика) ============

    @Test
    void execute_statsCommand_withTasks_showsCurrentProgress() {
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
        assertFalse(result.contains("💾 *Сохраненная:*"));
        assertTrue(result.contains("Статистика за сегодня"));
        // Погоды нет, потому что город не установлен
        assertFalse(result.contains("🌤️ Облачно"));
    }

    @Test
    void execute_statsCommand_noTasksButSavedStats_showsSavedProgress() {
        when(mockMessage.getText()).thenReturn("/stats");

        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(Collections.emptyList());
        when(mockDatabaseManager.getTodayStats(12345L)).thenReturn(75.5);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn("Moscow");

        // Мокаем прогноз для "Moscow"
        when(mockWeatherService.getTodayForecast("Moscow"))
                .thenReturn("🌤️ Солнечно, +22°C");

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("🏙️ *Город:* Moscow"));
        assertTrue(result.contains("✅ *Выполнено:* 0/0 задач"));
        assertTrue(result.contains("📈 *Сохраненная продуктивность:* 75,5%"));
        assertTrue(result.contains("💾 *Сохраненная:* 75,5%"));
        assertTrue(result.contains("🌤️ Солнечно, +22°C")); // ← погода добавлена
    }

    @Test
    void execute_statsCommand_noTasksNoSavedStats_showsZeroProgress() {
        when(mockMessage.getText()).thenReturn("/stats");

        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(Collections.emptyList());
        when(mockDatabaseManager.getTodayStats(12345L)).thenReturn(null);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn("Екатеринбург");

        when(mockWeatherService.getTodayForecast("Екатеринбург"))
                .thenReturn("🌧️ Дождь, +15°C");

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("🏙️ *Город:* Екатеринбург"));
        assertTrue(result.contains("✅ *Выполнено:* 0/0 задач"));
        assertTrue(result.contains("📈 *Продуктивность:* 0,0%"));
        assertTrue(result.contains("🌧️ Дождь, +15°C"));
        assertFalse(result.contains("💾 *Сохраненная:*"));
    }

    @Test
    void execute_statsCommand_noCity_showsPrompt() {
        when(mockMessage.getText()).thenReturn("/stats");

        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(Collections.emptyList());
        when(mockDatabaseManager.getTodayStats(12345L)).thenReturn(null);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn(null); // ← нет города

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("💡 Установите город: `/setcity Москва`"));
        assertFalse(result.contains("🌤️")); // никакой погоды
    }

    // ============ Тесты для /stats week ============

    @Test
    void execute_statsWeekCommand_withData_showsDetailedStats() {
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
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn("Новосибирск");

        when(mockWeatherService.getTodayForecast("Новосибирск"))
                .thenReturn("⛅ Переменная облачность, +20°C");

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("*📊 Статистика за неделю:*"));
        assertTrue(result.contains("🏙️ *Город:* Новосибирск"));
        assertTrue(result.contains("🌤️ *Погода сегодня:*"));
        assertTrue(result.contains("⛅ Переменная облачность, +20°C"));
        assertTrue(result.contains("📅 *Активных дней:* 2/7"));
        assertTrue(result.contains("📈 *Средняя продуктивность:* 75,0%"));
        assertTrue(result.contains("🗓️ *Неделя: 2025-06-02 – 2025-06-08*"));
    }

    @Test
    void execute_statsWeekCommand_noData_showsEmptyMessage() {
        when(mockMessage.getText()).thenReturn("/stats week");
        when(mockDatabaseManager.getWeeklyProductivityStats(12345L)).thenReturn(Collections.emptyList());
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn(null);

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("Нет данных за текущую неделю"));
        assertTrue(result.contains("Добавьте задачи с помощью `/todo add`"));
        assertFalse(result.contains("🌤️")); // погоды нет
    }

    @Test
    void execute_statsWeekCommand_withCity_showsCityInfo() {
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

        when(mockWeatherService.getTodayForecast("Екатеринбург"))
                .thenReturn("☀️ Ясно, +25°C");

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("🏙️ *Город:* Екатеринбург"));
        assertTrue(result.contains("☀️ Ясно, +25°C"));
    }

    // ============ Тесты для неверных аргументов ============

    @Test
    void execute_statsCommand_withInvalidArgument_showsHelp() {
        when(mockMessage.getText()).thenReturn("/stats abc");
        when(mockDatabaseManager.getDailyTasks(12345L)).thenReturn(Collections.emptyList());
        when(mockDatabaseManager.getTodayStats(12345L)).thenReturn(null);
        when(mockDatabaseManager.getUserCity(12345L)).thenReturn(null);

        String result = statsCommand.execute(mockMessage);

        assertTrue(result.contains("❓ *Неизвестный параметр:* 'abc'"));
        assertTrue(result.contains("Статистика за сегодня"));
        assertFalse(result.contains("🌤️"));
    }

    // ============ Тесты описания команды ============

    @Test
    void commandNameAndDescriptionShouldBeCorrect() {
        assertEquals("stats", statsCommand.getBotCommand().getCommand());
        assertEquals("Показать статистику выполнения", statsCommand.getDescription());
    }
}
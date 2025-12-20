
package com.example.bot.database;

import org.junit.jupiter.api.*;
import java.io.IOException;
import java.io.InputStream;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты для DatabaseManager
 */
class DatabaseManagerIntegrationTest {

    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;
    private DatabaseManager databaseManager;

    @BeforeAll
    static void loadDbConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream is = DatabaseManagerIntegrationTest.class
                .getClassLoader()
                .getResourceAsStream("database-test.properties")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Файл database-test.properties не найден в src/test/resources/"
                );
            }
            props.load(is);
        }
        DB_URL = props.getProperty("db.url");
        DB_USER = props.getProperty("db.user");
        DB_PASSWORD = props.getProperty("db.password");

        // Проверяем подключение
        try (var ignored = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("✅ Успешное подключение к тестовой БД: " + DB_URL);
        } catch (Exception e) {
            throw new IllegalStateException("Не удаётся подключиться к БД", e);
        }
    }
    @BeforeEach
    void setUp() {
        databaseManager = new DatabaseManager(DB_URL, DB_USER, DB_PASSWORD);
        cleanupDatabase(); // Очищаем перед каждым тестом
    }
    /**
     * Очищает все таблицы перед тестом
     */
    private void cleanupDatabase() {
        try (var conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (var stmt = conn.createStatement()) {
                // Важно: порядок DELETE должен учитывать foreign keys (у вас их нет, но на всякий случай)
                stmt.execute("DELETE FROM productivity_stats");
                stmt.execute("DELETE FROM wishlist_locks");
                stmt.execute("DELETE FROM wishlist");
                stmt.execute("DELETE FROM daily_tasks");
                stmt.execute("DELETE FROM users");
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка очистки БД", e);
        }
    }

    // ============ Тесты для задач (daily_tasks) ============

    @Test
    void addAndRetrieveDailyTasks() {
        // Given
        Long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");
        String taskText = "Тестовая задача";

        // When
        int taskId = databaseManager.addDailyTask(userId, taskText);
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);

        // Then
        assertNotEquals(-1, taskId);
        assertEquals(1, tasks.size());
        assertEquals(taskText, tasks.getFirst().getText());
        assertFalse(tasks.getFirst().isCompleted());
    }

    @Test
    void completeDailyTask_updatesStatus() {
        // Given
        Long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");
        int taskId = databaseManager.addDailyTask(userId, "Задача для завершения");

        // When
        boolean completed = databaseManager.completeDailyTask(userId, taskId);
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);

        // Then
        assertTrue(completed);
        assertTrue(tasks.getFirst().isCompleted());
    }

    @Test
    void updateDailyTask_modifiesText() {
        // Given
        Long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");
        int taskId = databaseManager.addDailyTask(userId, "Старый текст");

        // When
        boolean updated = databaseManager.updateDailyTask(userId, taskId, "Новый текст");
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);

        // Then
        assertTrue(updated);
        assertEquals("Новый текст", tasks.getFirst().getText());
    }

    @Test
    void cleanupAllDailyTasks_removesAllTasks() {
        // Given
        databaseManager.addDailyTask(12345L, "Задача 1");
        databaseManager.addDailyTask(12345L, "Задача 2");

        // When
        databaseManager.cleanupAllDailyTasks();
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(12345L);

        // Then
        assertTrue(tasks.isEmpty());
    }

    // ============ Тесты для карты желаний (wishlist) ============

    @Test
    void addAndRetrieveWishes() {
        // Given
        Long userId = 12345L;
        String wishText = "Тестовое желание";
        databaseManager.saveUser(userId, "testuser");
        // When
        int wishId = databaseManager.addWish(userId, wishText);
        List<DatabaseManager.Wish> wishes = databaseManager.getWishes(userId);

        // Then
        assertNotEquals(-1, wishId);
        assertEquals(1, wishes.size());
        assertEquals(wishText, wishes.getFirst().getText());
        assertFalse(wishes.getFirst().isCompleted());
    }
    @Test
    void cleanupUnlockedWishes_removesWishesWhenNotLocked() {
        // Given
        Long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");

        // Добавляем желания
        databaseManager.addWish(userId, "Желание 1");
        databaseManager.addWish(userId, "Желание 2");

        // Убеждаемся, что список НЕ заблокирован
        assertFalse(databaseManager.isWishlistLocked(userId));

        // When
        databaseManager.cleanupUnlockedWishes();
        List<DatabaseManager.Wish> wishes = databaseManager.getWishes(userId);

        // Then
        assertTrue(wishes.isEmpty(), "Желания должны быть удалены, так как список не заблокирован");
    }
    @Test
    void cleanupUnlockedWishes_doesNotRemoveWishesWhenLocked() {
        // Given
        Long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");

        databaseManager.addWish(userId, "Желание 1");
        databaseManager.addWish(userId, "Желание 2");

        // Блокируем список
        databaseManager.lockWishlist(userId);
        assertTrue(databaseManager.isWishlistLocked(userId));

        // When
        databaseManager.cleanupUnlockedWishes();
        List<DatabaseManager.Wish> wishes = databaseManager.getWishes(userId);

        // Then
        assertEquals(2, wishes.size(), "Желания не должны удаляться, так как список заблокирован");
        assertEquals("Желание 1", wishes.get(0).getText());
        assertEquals("Желание 2", wishes.get(1).getText());
    }

    @Test
    void completeWish_updatesStatus() {
        // Given
        Long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");
        int wishId = databaseManager.addWish(userId, "Желание для завершения");

        // When
        boolean completed = databaseManager.completeWish(userId, wishId);
        List<DatabaseManager.Wish> wishes = databaseManager.getWishes(userId);

        // Then
        assertTrue(completed);
        assertTrue(wishes.getFirst().isCompleted());
    }

    @Test
    void wishlistLocking_worksCorrectly() {
        // Given
        Long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");
        // When
        databaseManager.lockWishlist(userId);
        boolean isLocked = databaseManager.isWishlistLocked(userId);
        LocalDateTime lockUntil = databaseManager.getLockUntil(userId);

        // Then
        assertTrue(isLocked);
        assertNotNull(lockUntil);
        assertTrue(lockUntil.isAfter(LocalDateTime.now()));

        // Проверяем разблокировку
        databaseManager.unlockWishlist(userId);
        assertFalse(databaseManager.isWishlistLocked(userId));
    }

    // ============ Тесты для статистики продуктивности ============

    @Test
    void saveAndRetrieveProductivityStats() {
        // Given
        Long userId = 12345L;
        int completed = 2;
        int total = 5;

        // ✅ Сначала создаём пользователя
        databaseManager.saveUser(userId, "testuser");

        // When
        databaseManager.saveProductivityStats(userId, completed, total);
        Double todayStats = databaseManager.getTodayStats(userId);

        // Then
        assertNotNull(todayStats);
        assertEquals(40.0, todayStats, 0.01); // 2/5 = 40%
    }

    @Test
    void getWeeklyProductivityStats_returnsCurrentWeekData() {
        // Given
        Long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");
        // Сохраняем статистику за сегодня
        databaseManager.saveProductivityStats(userId, 3, 4); // 75%

        // When
        List<DatabaseManager.ProductivityStat> weeklyStats =
                databaseManager.getWeeklyProductivityStats(userId);

        // Then
        assertFalse(weeklyStats.isEmpty());
        assertEquals(75.0, weeklyStats.getFirst().getCompletionRate(), 0.01);
        assertEquals(4, weeklyStats.getFirst().getTotalTasks());
        assertEquals(3, weeklyStats.getFirst().getCompletedTasks());
    }
    @Test
    void saveAllUsersProductivityStats_savesStatsForUsersWithTodayTasks() {
        // Given
        Long user1 = 111L;
        Long user2 = 222L;
        Long userWithoutTasks = 333L;

        databaseManager.saveUser(user1, "user1");
        databaseManager.saveUser(user2, "user2");
        databaseManager.saveUser(userWithoutTasks, "user3");

        // ✅ Сохраняем реальные ID задач
        int taskId1 = databaseManager.addDailyTask(user1, "Задача 1");
        databaseManager.addDailyTask(user1, "Задача 2");
        databaseManager.addDailyTask(user2, "Задача A");

        // ✅ Завершаем задачу по её реальному ID
        databaseManager.completeDailyTask(user1, taskId1); // Завершаем первую задачу

        // When
        databaseManager.saveAllUsersProductivityStats();

        // Then
        Double stats1 = databaseManager.getTodayStats(user1);
        assertNotNull(stats1);
        assertEquals(50.0, stats1, 0.01); // 1 из 2 завершена

        Double stats2 = databaseManager.getTodayStats(user2);
        assertNotNull(stats2);
        assertEquals(0.0, stats2, 0.01); // 0 из 1

        Double stats3 = databaseManager.getTodayStats(userWithoutTasks);
        assertNull(stats3);

        // Проверка количества записей
        try (var conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             var stmt = conn.prepareStatement("SELECT COUNT(*) FROM productivity_stats WHERE stat_date = CURRENT_DATE");
             var rs = stmt.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        } catch (Exception e) {
            fail("Ошибка: " + e.getMessage());
        }
    }
    @Test
    void saveAllUsersProductivityStats_isIdempotent() {
        Long userId = 444L;
        databaseManager.saveUser(userId, "test");
        databaseManager.addDailyTask(userId, "Тестовая задача");
        // Задача не завершена → 0%

        // Вызываем дважды
        databaseManager.saveAllUsersProductivityStats();
        databaseManager.saveAllUsersProductivityStats();

        // Должна быть только одна запись
        String countSql = "SELECT COUNT(*) FROM productivity_stats WHERE user_id = ? AND stat_date = CURRENT_DATE";
        try (var conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             var stmt = conn.prepareStatement(countSql)) {
            stmt.setLong(1, userId);
            try (var rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Повторный вызов не должен создавать дубликаты");
            }
        } catch (Exception e) {
            fail("Ошибка проверки дубликатов: " + e.getMessage());
        }
    }

    @Test
    void cleanupOldProductivityStats_removesOldData() {
        // Given
        long userId = 12345L;
        databaseManager.saveUser(userId, "testuser");
        // Дата из текущей недели (например, понедельник текущей недели)
        LocalDate thisWeekDate = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        // Дата старше 14 дней
        LocalDate oldDate = LocalDate.now().minusDays(15);

        try (var conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             var stmt = conn.prepareStatement("""
             INSERT INTO productivity_stats
             (user_id, completion_rate, stat_date, total_tasks, completed_tasks)
             VALUES
             (?, 100.0, ?, 1, 1),  -- старая запись (должна быть удалена)
             (?, 50.0, ?, 2, 1)   -- запись из текущей недели (должна остаться)
             """)) {

            stmt.setLong(1, userId);
            stmt.setDate(2, java.sql.Date.valueOf(oldDate));
            stmt.setLong(3, userId);
            stmt.setDate(4, java.sql.Date.valueOf(thisWeekDate));

            stmt.executeUpdate();
        } catch (Exception e) {
            fail("Ошибка вставки данных: " + e.getMessage());
        }

        // When
        databaseManager.cleanupOldProductivityStats();

        // Получаем ВСЕ записи (не только за неделю!)
        String selectSql = "SELECT COUNT(*) FROM productivity_stats WHERE user_id = ?";
        int remainingCount;
        try (var conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             var stmt = conn.prepareStatement(selectSql)) {
            stmt.setLong(1, userId);
            try (var rs = stmt.executeQuery()) {
                rs.next();
                remainingCount = rs.getInt(1);
            }
        } catch (Exception e) {
            fail("Ошибка подсчёта записей: " + e.getMessage());
            return;
        }

        // Then
        assertEquals(1, remainingCount); // Только запись из текущей недели осталась
    }

    // ============ Тесты пользователей ============

    @Test
    void saveAndRetrieveUserCity() {
        Long userId = 12345L;
        String city = "Екатеринбург";

        databaseManager.saveUser(userId, "testuser");
        databaseManager.updateUserCity(userId, city);
        String retrievedCity = databaseManager.getUserCity(userId);

        assertEquals(city, retrievedCity);
    }
}

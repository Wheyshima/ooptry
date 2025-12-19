package com.example.bot.database;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private final String url;
    private final String username;
    private final String password;
    public static final int WISHLIST_LOCK_DAYS = 60;
    public record UserWithCity(long userId, String city) {} // DTO

    public DatabaseManager(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = getConnection()) {
            // Таблица пользователей
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id BIGINT PRIMARY KEY,
                    username VARCHAR(100),
                    city VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Таблица ежедневных задач
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS daily_tasks (
                    id SERIAL PRIMARY KEY,
                    user_id BIGINT REFERENCES users(user_id),
                    task_text TEXT NOT NULL,
                    completed BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            // Таблица с городами
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS user_city (
                    id BIGINT PRIMARY KEY,
                    city Text
                )
            """);

            // Таблица карты желаний
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS wishlist (
                    id SERIAL PRIMARY KEY,
                    user_id BIGINT REFERENCES users(user_id),
                    wish_text TEXT NOT NULL,
                    deadline TIMESTAMP,
                    completed BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            //таблица блокировок
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS wishlist_locks (
                    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
                    locked BOOLEAN DEFAULT FALSE,
                    lock_until TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            // Упрощенная таблица для статистики продуктивности (только процент)
            conn.createStatement().execute("DROP TABLE IF EXISTS productivity_stats");

            conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS productivity_stats (
                id SERIAL PRIMARY KEY,
                user_id BIGINT REFERENCES users(user_id),
                completion_rate DECIMAL(5,2) NOT NULL,
                stat_date DATE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, stat_date)
            )
        """);

            System.out.println("База данных инициализирована успешно");

        } catch (SQLException e) {
            System.err.println("Ошибка инициализации БД: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    // Методы для пользователей
    public void saveUser(Long userId, String username) {
        String sql = "INSERT INTO users (user_id, username) VALUES (?, ?) ON CONFLICT (user_id) DO UPDATE SET username = EXCLUDED.username";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка сохранения пользователя: " + e.getMessage());
        }
    }
 // для отправки рассылки юзерам с городом
    public void updateUserCity(Long userId, String city) {
        String sql = "UPDATE users SET city = ? WHERE user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, city);
            stmt.setLong(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка обновления города: " + e.getMessage());
        }
    }

    public List<UserWithCity> getAllUsersWithCities() {
        List<UserWithCity> users = new ArrayList<>();
        String sql = "SELECT user_id, city FROM users WHERE city IS NOT NULL AND city != ''";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                users.add(new UserWithCity(rs.getLong("user_id"), rs.getString("city")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public List<Long> getAllUserIds() {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT DISTINCT user_id FROM users";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения списка пользователей: " + e.getMessage());
            e.printStackTrace();
        }
        return ids;
    }

    public void cleanupExpiredDailyTasks() {
        try (Connection conn = getConnection()) {
            // Удаляем только задачи, созданные ДО сегодняшнего дня
            String deleteSql = "DELETE FROM daily_tasks WHERE DATE(created_at) < CURRENT_DATE";
            int deletedCount = conn.createStatement().executeUpdate(deleteSql);

            System.out.println("🗑️ Удалено " + deletedCount + " задач предыдущих дней");

        } catch (SQLException e) {
            System.err.println("❌ Ошибка при очистке задач: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void cleanupAllDailyTasks() {
        try (Connection conn = getConnection()) {
            // TRUNCATE удаляет все данные и автоматически сбрасывает sequence
            String truncateSql = "TRUNCATE TABLE daily_tasks RESTART IDENTITY";
            conn.createStatement().executeUpdate(truncateSql);

            System.out.println("🧹 Таблица daily_tasks полностью очищена, ID сброшены к 1");

        } catch (SQLException e) {
            System.err.println("❌ Ошибка при принудительной очистке: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getUserCity(Long userId) {
        String sql = "SELECT city FROM users WHERE user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("city");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения города: " + e.getMessage());
        }
        return null;
    }

    // Методы для ежедневных задач
    public int addDailyTask(Long userId, String taskText) {
        String sql = "INSERT INTO daily_tasks (user_id, task_text) VALUES (?, ?) RETURNING id";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, taskText);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка добавления задачи: " + e.getMessage());
        }
        return -1;
    }

    public boolean updateDailyTask(Long userId, int taskId, String newText) {
        // Используем правильное имя столбца - task_text
        String sql = "UPDATE daily_tasks SET task_text = ? WHERE id = ? AND user_id = ? AND completed = false";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newText);
            pstmt.setInt(2, taskId);
            pstmt.setLong(3, userId);

            int rowsUpdated = pstmt.executeUpdate();
            return rowsUpdated > 0;

        } catch (SQLException e) {
            System.err.println("Ошибка SQL при обновлении задачи: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to update daily task", e);
        }
    }

    public boolean completeDailyTask(Long userId, int taskId) {
        String sql = "UPDATE daily_tasks SET completed = TRUE WHERE id = ? AND user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, taskId);
            stmt.setLong(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Ошибка завершения задачи: " + e.getMessage());
        }
        return false;
    }

    public List<Task> getDailyTasks(Long userId) {
        List<Task> tasks = new ArrayList<>();
        // Показываем все сегодняшние задачи
        String sql = "SELECT id, task_text, completed, created_at FROM daily_tasks WHERE user_id = ? AND DATE(created_at) = CURRENT_DATE ORDER BY created_at";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                tasks.add(new Task(
                        rs.getInt("id"),
                        rs.getString("task_text"),
                        rs.getBoolean("completed"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения задач: " + e.getMessage());
        }
        return tasks;
    }

    // Методы для карты желаний
    public int addWish(Long userId, String wishText, LocalDateTime deadline) {
        String sql = "INSERT INTO wishlist (user_id, wish_text, deadline) VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, wishText);
            stmt.setTimestamp(3, deadline != null ? Timestamp.valueOf(deadline) : null);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка добавления желания: " + e.getMessage());
        }
        return -1;
    }

    public List<Wish> getWishes(Long userId) {
        List<Wish> wishes = new ArrayList<>();
        String sql = "SELECT id, wish_text, deadline, completed, created_at FROM wishlist WHERE user_id = ? ORDER BY created_at";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                wishes.add(new Wish(
                        rs.getInt("id"),
                        rs.getString("wish_text"),
                        rs.getTimestamp("deadline") != null ? rs.getTimestamp("deadline").toLocalDateTime() : null,
                        rs.getBoolean("completed"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения желаний: " + e.getMessage());
        }
        return wishes;
    }

    public int getWishCount(Long userId) {
        String sql = "SELECT COUNT(*) as count FROM wishlist WHERE user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения количества желаний: " + e.getMessage());
        }
        return 0;
    }

    public boolean completeWish(Long userId, int wishId) {
        String sql = "UPDATE wishlist SET completed = TRUE WHERE id = ? AND user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, wishId);
            stmt.setLong(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Ошибка завершения желания: " + e.getMessage());
        }
        return false;
    }


    public boolean isWishlistLocked(Long userId) {
        String sql = "SELECT locked, lock_until FROM wishlist_locks WHERE user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                boolean locked = rs.getBoolean("locked");
                Timestamp lockUntil = rs.getTimestamp("lock_until");

                // Проверяем не истек ли срок блокировки
                if (locked && lockUntil != null && lockUntil.toLocalDateTime().isAfter(LocalDateTime.now())) {
                    return true;
                } else if (locked) {
                    // Срок истек - разблокируем
                    unlockWishlist(userId);
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка проверки блокировки: " + e.getMessage());
        }
        System.out.println("❌ Блокировка не найдена или неактивна");
        return false;
    }

    public void lockWishlist(Long userId) {
        // Исправленный SQL - убираем кавычки вокруг параметра
        String sql = "INSERT INTO wishlist_locks (user_id, locked, lock_until) " +
                "VALUES (?, TRUE, CURRENT_TIMESTAMP + INTERVAL '" + WISHLIST_LOCK_DAYS  + " days') " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "locked = TRUE, lock_until = CURRENT_TIMESTAMP + INTERVAL '" + WISHLIST_LOCK_DAYS  + " days'";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();
            System.out.println("🔒 Пользователь " + userId + " заблокирован на " + WISHLIST_LOCK_DAYS + " дней");
        } catch (SQLException e) {
            System.err.println("Ошибка блокировки wishlist: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void unlockWishlist(Long userId) {
        String sql = "UPDATE wishlist_locks SET locked = FALSE WHERE user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка разблокировки wishlist: " + e.getMessage());
        }
    }

    public void resetWishlist() {
        try (Connection conn = getConnection()) {
            // Сбрасываем основную таблицу wishlist
            String resetWishlistSQL = "DELETE FROM wishlist";
            try (PreparedStatement stmt = conn.prepareStatement(resetWishlistSQL)) {
                stmt.executeUpdate();
            }

            // Сбрасываем блокировки
            String resetLocksSQL = "UPDATE wishlist_locks SET locked = false, lock_until = null";
            try (PreparedStatement stmt = conn.prepareStatement(resetLocksSQL)) {
                stmt.executeUpdate();
            }

            String resetSequenceSQL = "ALTER SEQUENCE wishlist_id_seq RESTART WITH 1";
            try (PreparedStatement stmt = conn.prepareStatement(resetSequenceSQL)) {
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            System.err.println("Ошибка сброса wishlist: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public LocalDateTime getLockUntil(Long userId) {
        String sql = "SELECT lock_until FROM wishlist_locks WHERE user_id = ? AND locked = TRUE";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Timestamp lockUntil = rs.getTimestamp("lock_until");
                return lockUntil != null ? lockUntil.toLocalDateTime() : null;
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения срока блокировки: " + e.getMessage());
        }
        return null;
    }

    public void cleanupExpiredWishes() {
        try (Connection conn = getConnection()) {
            // Удаляем желания, у которых истек срок блокировки
            String deleteSql = """
                DELETE FROM wishlist
                WHERE user_id IN (
                    SELECT user_id FROM wishlist_locks
                    WHERE locked = TRUE AND lock_until < CURRENT_TIMESTAMP
                )
                """;
            int deletedCount = conn.createStatement().executeUpdate(deleteSql);

            // Разблокируем пользователей после удаления
            String unlockSql = "UPDATE wishlist_locks SET locked = FALSE WHERE lock_until < CURRENT_TIMESTAMP";
            int unlockedCount = conn.createStatement().executeUpdate(unlockSql);

            if (deletedCount > 0 || unlockedCount > 0) {
                System.out.println("🗑️ Удалено " + deletedCount + " устаревших желаний, разблокировано " + unlockedCount + " пользователей");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка при очистке устаревших желаний: " + e.getMessage());
        }
    }

    public void cleanupUnlockedWishes() {
        try (Connection conn = getConnection()) {
            // Удаляем ВСЕ желания пользователей, у которых нет активной блокировки
            String deleteSql = """
                DELETE FROM wishlist
                WHERE user_id NOT IN (
                    SELECT user_id FROM wishlist_locks WHERE locked = TRUE
                )
                """;

            int deletedCount = conn.createStatement().executeUpdate(deleteSql);

            if (deletedCount > 0) {
                System.out.println("🗑️ Удалено " + deletedCount + " незаблокированных желаний");
            } else {
                System.out.println("✅ Нет незаблокированных желаний для удаления");
            }

        } catch (SQLException e) {
            System.err.println("❌ Ошибка при очистке незаблокированных желаний: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Методы для статистики
    public double getDailyCompletionRate(Long userId) {
        String sql = """
            SELECT
                COALESCE(
                    ROUND(
                        (COUNT(CASE WHEN completed = TRUE THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0)
                    ), 2
                ), 0.0) as completion_rate
            FROM daily_tasks
            WHERE user_id = ? AND DATE(created_at) = CURRENT_DATE
        """;
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("completion_rate");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения статистики: " + e.getMessage());
        }
        return 0.0;
    }
/// ///////

    public TaskStats getTaskStats() {
        try (Connection conn = getConnection()) {
            String sql = """
                SELECT
                    COUNT(*) as total_tasks,
                    COUNT(CASE WHEN DATE(created_at) < CURRENT_DATE THEN 1 END) as old_tasks,
                    COUNT(CASE WHEN DATE(created_at) = CURRENT_DATE THEN 1 END) as today_tasks
                FROM daily_tasks
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new TaskStats(
                            rs.getInt("total_tasks"),
                            rs.getInt("old_tasks"),
                            rs.getInt("today_tasks")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения статистики: " + e.getMessage());
        }
        return new TaskStats(0, 0, 0);
    }

    /**
     * Публичный метод для получения количества сегодняшних задач
     */
    public int getTodayTasksCount() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT COUNT(*) as today_tasks FROM daily_tasks WHERE DATE(created_at) = CURRENT_DATE";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("today_tasks");
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения количества задач: " + e.getMessage());
        }
        return 0;
    }

    // Метод для сохранения/обновления статистики (только процент)
    public void saveProductivityStats(Long userId, double completionRate) {
        String sql = """
            INSERT INTO productivity_stats (user_id, completion_rate, stat_date)
            VALUES (?, ?, CURRENT_DATE)
            ON CONFLICT (user_id, stat_date) DO UPDATE SET
                completion_rate = EXCLUDED.completion_rate,
                created_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setDouble(2, completionRate);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка сохранения статистики: " + e.getMessage());
        }
    }

    // Метод для получения статистики пользователя за сегодня
    public Double getTodayStats(Long userId) {
        String sql = "SELECT completion_rate FROM productivity_stats WHERE user_id = ? AND stat_date = CURRENT_DATE";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getDouble("completion_rate");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения статистики: " + e.getMessage());
        }
        return null; // Возвращаем null если статистики нет
    }

    // Метод для получения статистики за неделю (последние 7 дней)
    public List<Double> getWeeklyStats(Long userId) {
        List<Double> stats = new ArrayList<>();
        String sql = "SELECT completion_rate FROM productivity_stats " +
                "WHERE user_id = ? AND stat_date >= CURRENT_DATE - INTERVAL '7 days' " +
                "ORDER BY stat_date DESC";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                stats.add(rs.getDouble("completion_rate"));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения недельной статистики: " + e.getMessage());
        }
        return stats;
    }


    // Метод для сохранения статистики всех активных пользователей
    public void saveAllUsersProductivityStats() {
        try (Connection conn = getConnection()) {
            // Получаем всех пользователей, у которых есть задачи сегодня
            String usersSql = "SELECT DISTINCT user_id FROM daily_tasks WHERE DATE(created_at) = CURRENT_DATE";
            List<Long> activeUserIds = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(usersSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    activeUserIds.add(rs.getLong("user_id"));
                }
            }

            // Сохраняем статистику для каждого активного пользователя
            int savedCount = 0;
            for (Long userId : activeUserIds) {
                double completionRate = getDailyCompletionRate(userId);
                if (!Double.isNaN(completionRate)) { // Сохраняем только если есть задачи
                    saveProductivityStats(userId, completionRate);
                    savedCount++;
                }
            }

            System.out.println("💾 Сохранена статистика для " + savedCount + " пользователей");

        } catch (SQLException e) {
            System.err.println("❌ Ошибка при сохранении статистики всех пользователей: " + e.getMessage());
        }
    }

    /**
     * Класс для хранения статистики
     */
    public static class TaskStats {
        public final int totalTasks;
        public final int oldTasks;
        public final int todayTasks;

        public TaskStats(int totalTasks, int oldTasks, int todayTasks) {
            this.totalTasks = totalTasks;
            this.oldTasks = oldTasks;
            this.todayTasks = todayTasks;
        }
    }
    // Классы-модели
    public static class Task {
        private final int id;
        private final String text;
        private final boolean completed;
        private final LocalDateTime createdAt;

        public Task(int id, String text, boolean completed, LocalDateTime createdAt) {
            this.id = id;
            this.text = text;
            this.completed = completed;
            this.createdAt = createdAt;
        }

        public int getId() { return id; }
        public String getText() { return text; }
        public boolean isCompleted() { return completed; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    public static class Wish {
        private final int id;
        private final String text;
        private final LocalDateTime deadline;
        private final boolean completed;
        private final LocalDateTime createdAt;

        public Wish(int id, String text, LocalDateTime deadline, boolean completed, LocalDateTime createdAt) {
            this.id = id;
            this.text = text;
            this.deadline = deadline;
            this.completed = completed;
            this.createdAt = createdAt;
        }

        public int getId() { return id; }
        public String getText() { return text; }
        public LocalDateTime getDeadline() { return deadline; }
        public boolean isCompleted() { return completed; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

}
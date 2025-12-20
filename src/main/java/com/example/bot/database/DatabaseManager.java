package com.example.bot.database;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("CallToPrintStackTrace")
public class DatabaseManager {
    private final String url;
    private final String username;
    private final String password;
    public static final int WISHLIST_LOCK_DAYS = 60;

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
        @SuppressWarnings("unused")
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
    public static class Wish {
        private final int id;
        private final String text;
        private final boolean completed;
        private final LocalDateTime createdAt;

        public Wish(int id, String text, boolean completed, LocalDateTime createdAt) {
            this.id = id;
            this.text = text;
            this.completed = completed;
            this.createdAt = createdAt;
        }

        public int getId() { return id; }
        public String getText() { return text; }
        public boolean isCompleted() { return completed; }
        @SuppressWarnings("unused")
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    /**
     * Класс для хранения ежедневной статистики продуктивности
     */
    public static class ProductivityStat {
        public final double completionRate;
        public final LocalDate statDate;
        public final LocalDateTime createdAt;
        public final int totalTasks;
        public final int completedTasks;

        public ProductivityStat(double completionRate, LocalDate statDate, LocalDateTime createdAt,
                                int totalTasks, int completedTasks) {
            this.completionRate = completionRate;
            this.statDate = statDate;
            this.createdAt = createdAt;
            this.totalTasks = totalTasks;
            this.completedTasks = completedTasks;
        }

        // Геттеры (опционально, но рекомендуются)
        public double getCompletionRate() { return completionRate; }
        public LocalDate getStatDate() { return statDate; }
        @SuppressWarnings("unused")
        public LocalDateTime getCreatedAt() { return createdAt; }
        public int getTotalTasks() { return totalTasks; }
        public int getCompletedTasks() { return completedTasks; }
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


    // === ВЛОЖЕННЫЕ КЛАССЫ ===

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

            // Таблица карты желаний
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS wishlist (
                    id SERIAL PRIMARY KEY,
                    user_id BIGINT REFERENCES users(user_id),
                    wish_text TEXT NOT NULL,
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

            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS productivity_stats (
                    id SERIAL PRIMARY KEY,
                    user_id BIGINT REFERENCES users(user_id),
                    completion_rate DECIMAL(5,2) NOT NULL,
                    stat_date DATE NOT NULL,
                    total_tasks INT DEFAULT 0,
                    completed_tasks INT DEFAULT 0,
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
    public void cleanupOldProductivityStats() {
        String sql = """
        DELETE FROM productivity_stats
        WHERE stat_date < CURRENT_DATE - INTERVAL '14 days'
        """;
        // Удаляем всё старше 14 дней
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                System.out.println("Очищено старых записей статистики: " + deleted);
            }
        } catch (SQLException e) {
            System.err.println("Ошибка очистки старой статистики: " + e.getMessage());
        }
    }
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
    public List<ProductivityStat> getWeeklyProductivityStats(Long userId) {
        List<ProductivityStat> stats = new ArrayList<>();
        String sql = """
        SELECT completion_rate, stat_date,
               COALESCE(total_tasks, 0) AS total_tasks,
               COALESCE(completed_tasks, 0) AS completed_tasks,
               created_at
        FROM productivity_stats
        WHERE user_id = ?
          AND stat_date >= DATE_TRUNC('week', CURRENT_DATE)::DATE
          AND stat_date <= DATE_TRUNC('week', CURRENT_DATE)::DATE + INTERVAL '6 days'
        ORDER BY stat_date ASC
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                stats.add(new ProductivityStat(
                        rs.getDouble("completion_rate"),
                        rs.getDate("stat_date").toLocalDate(),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getInt("total_tasks"),
                        rs.getInt("completed_tasks")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения недельной статистики: " + e.getMessage());
        }
        return stats;
    }
    // В DatabaseManager.java
    public List<Long> getAllUserIds() {
        String sql = "SELECT user_id FROM users";
        List<Long> userIds = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                userIds.add(rs.getLong("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения списка пользователей"+ e.getMessage());
        }
        return userIds;
    }

    public void cleanupAllDailyTasks() {
        try (Connection conn = getConnection()) {
            String sql = "DELETE FROM daily_tasks";
            int deleted = conn.createStatement().executeUpdate(sql);
            System.out.println("🧹 Удалено задач: " + deleted);
        } catch (SQLException e) {
            System.err.println(" Ошибка при принудительной очистке: " + e.getMessage());
            //noinspection CallToPrintStackTrace
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
                int taskId = rs.getInt("id");
                //  Сохраняем статистику после добавления задачи
                saveCurrentStats(userId);
                return taskId;
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
            boolean result = stmt.executeUpdate() > 0;

            //  СОХРАНЯЕМ СТАТИСТИКУ ПОСЛЕ ЗАВЕРШЕНИЯ ЗАДАЧИ
            if (result) {
                saveCurrentStats(userId);
            }

            return result;
        } catch (SQLException e) {
            System.err.println("Ошибка завершения задачи: " + e.getMessage());
        }
        return false;
    }

    public void saveCurrentStats(Long userId) {
        // Получаем задачи пользователя за сегодня
        List<Task> tasks = getDailyTasks(userId);
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(Task::isCompleted).count();

        if (totalTasks > 0 || completedTasks > 0) {
            saveProductivityStats(userId, completedTasks, totalTasks);
            double rate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;
            System.out.println("📊 Сохранена статистика для пользователя " + userId + ": " + String.format("%.2f", rate) + "% (" + completedTasks + "/" + totalTasks + ")");
        } else {
            System.out.println("ℹ️ Нет задач для пользователя " + userId + " — сохранение пропущено");
        }
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
    public int addWish(Long userId, String wishText) {
        String sql = "INSERT INTO wishlist (user_id, wish_text) VALUES (?, ?) RETURNING id";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, wishText);
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
        String sql = "SELECT id, wish_text, completed, created_at FROM wishlist WHERE user_id = ? ORDER BY created_at";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                wishes.add(new Wish(
                        rs.getInt("id"),
                        rs.getString("wish_text"),
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
        System.out.println(" Блокировка не найдена или неактивна");
        return false;
    }

    public void lockWishlist(Long userId) {
        String sql = "INSERT INTO wishlist_locks (user_id, locked, lock_until) " +
                "VALUES (?, TRUE, DATE_TRUNC('day', CURRENT_TIMESTAMP + INTERVAL '" + WISHLIST_LOCK_DAYS + " days') + INTERVAL '23 hours 59 minutes') " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "locked = TRUE, lock_until = DATE_TRUNC('day', CURRENT_TIMESTAMP + INTERVAL '" + WISHLIST_LOCK_DAYS + " days') + INTERVAL '23 hours 59 minutes'";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();

            // Логируем время блокировки
            String checkSql = "SELECT lock_until FROM wishlist_locks WHERE user_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setLong(1, userId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    Timestamp lockUntil = rs.getTimestamp("lock_until");
                    System.out.println("🔒 Пользователь " + userId + " заблокирован на " + WISHLIST_LOCK_DAYS + " дней (до " + lockUntil + ")");
                }
            }
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
                System.out.println(" Удалено " + deletedCount + " незаблокированных желаний");
            } else {
                System.out.println(" Нет незаблокированных желаний для удаления");
            }

        } catch (SQLException e) {
            System.err.println(" Ошибка при очистке незаблокированных желаний: " + e.getMessage());
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
    public void saveProductivityStats(Long userId, int completedTasks, int totalTasks) {
        double rate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;
        String sql = """
        INSERT INTO productivity_stats
            (user_id, completion_rate, stat_date, total_tasks, completed_tasks)
        VALUES (?, ?, CURRENT_DATE, ?, ?)
        ON CONFLICT (user_id, stat_date)
        DO UPDATE SET
            completion_rate = EXCLUDED.completion_rate,
            total_tasks = EXCLUDED.total_tasks,
            completed_tasks = EXCLUDED.completed_tasks,
            created_at = CURRENT_TIMESTAMP
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setDouble(2, Math.round(rate * 100.0) / 100.0); // округление до 2 знаков
            stmt.setInt(3, totalTasks);
            stmt.setInt(4, completedTasks);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка сохранения статистики: " + e.getMessage());
        }
    }
    // В DatabaseManager.java
    public List<Long> getUsersWithIncompleteTasks() {
        String sql = """
        SELECT DISTINCT user_id
        FROM daily_tasks
        WHERE completed = false
          AND DATE(created_at) = CURRENT_DATE
        """;
        List<Long> userIds = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                userIds.add(rs.getLong("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения пользователей с задачами"+ e.getMessage());
        }
        return userIds;
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


    // Метод для сохранения статистики всех активных пользователей
    public void saveAllUsersProductivityStats() {
        try (Connection conn = getConnection()) {
            // Получаем всех пользователей, у которых есть задачи за сегодня
            String usersSql = "SELECT DISTINCT user_id FROM daily_tasks WHERE DATE(created_at) = CURRENT_DATE";
            List<Long> activeUserIds = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(usersSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    activeUserIds.add(rs.getLong("user_id"));
                }
            }

            System.out.println("👥 Найдено активных пользователей с задачами: " + activeUserIds.size());

            int savedCount = 0;
            for (Long userId : activeUserIds) {
                List<Task> tasks = getDailyTasks(userId);
                int totalTasks = tasks.size();
                int completedTasks = (int) tasks.stream().filter(Task::isCompleted).count();

                saveProductivityStats(userId, completedTasks, totalTasks);
                savedCount++;

                double rate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0.0;
                System.out.println("   → " + userId + ": " + String.format("%.2f", rate) + "% (" + completedTasks + "/" + totalTasks + ")");
            }

            System.out.println("✅ Сохранена статистика для " + savedCount + " пользователей");

        } catch (SQLException e) {
            System.err.println("❌ Ошибка при сохранении статистики всех пользователей: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
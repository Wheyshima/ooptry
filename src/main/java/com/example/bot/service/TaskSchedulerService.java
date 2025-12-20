package com.example.bot.service;

import com.example.bot.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(TaskSchedulerService.class);

    private final ScheduledExecutorService scheduler;
    private final DatabaseManager databaseManager;
    private final MorningNewsletterService newsletterService;
    private final MessageSender messageSender;
    private final Clock clock; // ← добавлено

    // Основной конструктор (для продакшена)
    public TaskSchedulerService(DatabaseManager databaseManager,
                                MorningNewsletterService newsletterService,
                                MessageSender messageSender) {
        this(databaseManager, newsletterService, messageSender, Clock.systemDefaultZone());
    }

    // Пакетно-видимый конструктор для тестов
    TaskSchedulerService(DatabaseManager databaseManager,
                         MorningNewsletterService newsletterService,
                         MessageSender messageSender,
                         Clock clock) {
        this.databaseManager = databaseManager;
        this.newsletterService = newsletterService;
        this.messageSender = messageSender;
        this.clock = clock;
        this.scheduler = createScheduler();
    }

    protected ScheduledExecutorService createScheduler() {
        return Executors.newScheduledThreadPool(3);
    }

    public void startAllTasks() {
        startDailyCleanupTask();
        startMorningNewsletter();
        startReminderTasks();
    }

    public void shutdown() {
        logger.info("Завершение работы планировщика задач...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warn("Принудительное завершение планировщика задач");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Поток был прерван при завершении работы планировщика");
        }
    }

    // === Ежедневная очистка ===

    private void startDailyCleanupTask() {
        ZoneId tz = ZoneId.of("Asia/Yekaterinburg");
        ZonedDateTime now = now(tz);
        ZonedDateTime nextCleanup = now.toLocalDate().atTime(23, 59).atZone(tz);
        if (now.isAfter(nextCleanup)) {
            nextCleanup = nextCleanup.plusDays(1);
        }
        long initialDelay = Duration.between(now, nextCleanup).getSeconds();

        logger.info("""
            ⏰ Настройка ежедневной очистки:
               Текущее время сервера: {}
               Текущее время UTC+5: {}
               Следующая очистка: {}
               Задержка до очистки: {} секунд ({} часов)""",
                LocalDateTime.now(clock), // ← используем clock
                now,
                nextCleanup,
                initialDelay,
                String.format("%.2f", initialDelay / 3600.0));

        scheduler.scheduleAtFixedRate(
                this::performDailyCleanup,
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
    }

    // === Вспомогательный метод для времени ===
    private ZonedDateTime now(ZoneId tz) {
        return ZonedDateTime.now(clock).withZoneSameInstant(tz);
    }

    private void performDailyCleanup() {
        try {
            ZoneId tz = ZoneId.of("Asia/Yekaterinburg");
            ZonedDateTime cleanupTime = now(tz);
            logger.info("Запуск ежедневной очистки задач в {} (UTC+5)", cleanupTime);

            performCleanupOperations();

            var stats = databaseManager.getTaskStats();
            logger.info("До очистки: {} всего, {} устаревших, {} сегодняшних",
                    stats.totalTasks, stats.oldTasks, stats.todayTasks);

            int todayTasksAfter = databaseManager.getTodayTasksCount();
            logger.info("После очистки: {} сегодняшних задач сохранено", todayTasksAfter);
            logger.info("Ежедневная очистка завершена");

        } catch (Exception e) {
            logger.error("Ошибка при ежедневной очистке", e);
        }
    }

    private void performCleanupOperations() {
        databaseManager.cleanupOldProductivityStats();
        databaseManager.saveAllUsersProductivityStats();
        databaseManager.cleanupAllDailyTasks();
        databaseManager.cleanupUnlockedWishes();
    }

    // === Утренняя рассылка ===

    private void startMorningNewsletter() {
        ZoneId tz = ZoneId.of("Asia/Yekaterinburg");
        ZonedDateTime now = now(tz);
        LocalTime sendTime = LocalTime.of(7, 0); //

        ZonedDateTime nextRun = now.toLocalDate().atTime(sendTime).atZone(tz);
        if (now.toLocalTime().isAfter(sendTime)) {
            nextRun = nextRun.plusDays(1);
        }

        long initialDelay = Duration.between(now, nextRun).getSeconds();

        scheduler.scheduleAtFixedRate(
                newsletterService::sendNewsletterToAllUsers,
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );

        logger.info("📧 Утренняя рассылка запланирована на 08:30 (UTC+5)");
    }

    // === Напоминания ===

    private void startReminderTasks() {
        ZoneId tz = ZoneId.of("Asia/Yekaterinburg");
        ZonedDateTime now = now(tz);

        scheduleDailyTask(() -> sendReminderToAllUsers("1h"), now, LocalTime.of(22, 59), tz);
        scheduleDailyTask(() -> sendReminderToAllUsers("5m"), now, LocalTime.of(23, 54), tz);
    }

    private void scheduleDailyTask(Runnable task, ZonedDateTime now, LocalTime targetTime, ZoneId tz) {
        ZonedDateTime nextRun = now.toLocalDate().atTime(targetTime).atZone(tz);
        if (now.toLocalTime().isAfter(targetTime)) {
            nextRun = nextRun.plusDays(1);
        }
        long initialDelay = Duration.between(now, nextRun).getSeconds();

        scheduler.scheduleAtFixedRate(task, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        logger.info("⏰ Запланировано ежедневное напоминание на {}: {} секунд до первого запуска",
                targetTime, initialDelay);
    }

    // Остальное без изменений
    protected void sendReminderToAllUsers(String type) {
        try {
            logger.info("🔔 Отправка напоминаний типа '{}' всем пользователям с невыполненными задачами", type);

            List<Long> userIds = databaseManager.getUsersWithIncompleteTasks();
            String messageText = switch (type) {
                case "1h" -> """
                    ⏰ *Напоминание о задачах*
                    
                    У вас остались невыполненные задачи на сегодня!
                    Через 1 час они будут удалены.
                    
                    Не забудьте завершить важное! ✅
                    """;
                case "5m" -> """
                    ⚠️ *Последнее напоминание*
                    
                    Через 5 минут все невыполненные задачи сегодняшнего дня будут удалены.
                    
                    Успейте завершить, если нужно! 🚀
                    """;
                default -> "У вас есть невыполненные задачи.";
            };

            int sentCount = 0;
            for (Long userId : userIds) {
                try {
                    messageSender.sendText(userId, messageText);
                    sentCount++;
                } catch (Exception e) {
                    logger.warn("Не удалось отправить напоминание пользователю {}: {}", userId, e.getMessage());
                }
            }
            logger.info("✅ Отправлено {} напоминаний типа '{}'", sentCount, type);
        } catch (Exception e) {
            logger.error("Ошибка при отправке напоминаний", e);
        }
    }
}
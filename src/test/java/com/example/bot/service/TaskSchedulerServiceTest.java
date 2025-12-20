package com.example.bot.service;

import com.example.bot.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskSchedulerServiceTest {

    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private MorningNewsletterService mockNewsletterService;
    @Mock
    private MessageSender mockMessageSender;
    @Mock
    private ScheduledExecutorService mockScheduler;

    private static final ZoneId TZ = ZoneId.of("Asia/Yekaterinburg");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void startDailyCleanupTask_schedulesCleanupAt23_59() {
        // GIVEN: фиксированное время — 2025-12-16 20:00 UTC+5
        Instant fixedInstant = ZonedDateTime.of(2025, 12, 16, 20, 0, 0, 0, TZ).toInstant();
        Clock fixedClock = Clock.fixed(fixedInstant, TZ);

        TaskSchedulerService service = new TaskSchedulerService(
                mockDatabaseManager, mockNewsletterService, mockMessageSender, fixedClock) {
            @Override
            protected ScheduledExecutorService createScheduler() {
                return mockScheduler;
            }
        };

        // WHEN
        service.startAllTasks();

        // THEN: проверяем отложенный запуск задачи очистки
        verify(mockScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(14340L), // (23:59 - 20:00) = 3ч 59м = 14340 сек
                eq(TimeUnit.DAYS.toSeconds(1)),
                eq(TimeUnit.SECONDS)
        );

        // Выполняем задачу вручную и проверяем вызовы
        Runnable cleanupTask = captureRunnable(mockScheduler, 0);
        cleanupTask.run();

        verify(mockDatabaseManager).cleanupOldProductivityStats();
        verify(mockDatabaseManager).saveAllUsersProductivityStats();
        verify(mockDatabaseManager).cleanupAllDailyTasks();
        verify(mockDatabaseManager).cleanupUnlockedWishes();
    }

    @Test
    void startMorningNewsletter_schedulesAt08_30() {
        // GIVEN: сейчас 2025-12-16 20:00 → рассылка завтра в 08:30
        Instant fixedInstant = ZonedDateTime.of(2025, 12, 16, 20, 0, 0, 0, TZ).toInstant();
        Clock fixedClock = Clock.fixed(fixedInstant, TZ);

        TaskSchedulerService service = new TaskSchedulerService(
                mockDatabaseManager, mockNewsletterService, mockMessageSender, fixedClock) {
            @Override
            protected ScheduledExecutorService createScheduler() {
                return mockScheduler;
            }
        };

        service.startAllTasks();

        // Утренняя рассылка — это ВТОРОЙ вызов scheduleAtFixedRate
        // Проверяем именно его через capture
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);

        verify(mockScheduler, times(4)).scheduleAtFixedRate(
                captor.capture(),
                delayCaptor.capture(),
                anyLong(),
                eq(TimeUnit.SECONDS)
        );

        // Рассылка — вторая по порядку
        Runnable newsletterTask = captor.getAllValues().get(1);
        Long actualDelay = delayCaptor.getAllValues().get(1);

        assertEquals(39600L, actualDelay, "Задержка до 07:00 должна быть 45000 сек (12ч30м)");

        // Выполняем задачу
        newsletterTask.run();
        verify(mockNewsletterService).sendNewsletterToAllUsers();
    }

    @Test
    void sendReminderToAllUsers_sendsMessages() {
        // GIVEN
        when(mockDatabaseManager.getUsersWithIncompleteTasks())
                .thenReturn(java.util.List.of(100L, 200L));

        TaskSchedulerService service = new TaskSchedulerService(
                mockDatabaseManager, mockNewsletterService, mockMessageSender, Clock.systemUTC());

        // WHEN
        service.sendReminderToAllUsers("1h");

        // THEN
        verify(mockMessageSender).sendText(eq(100L), anyString());
        verify(mockMessageSender).sendText(eq(200L), anyString());
    }

    @Test
    void shutdown_stopsScheduler() throws InterruptedException {
        TaskSchedulerService service = new TaskSchedulerService(
                mockDatabaseManager, mockNewsletterService, mockMessageSender, Clock.systemUTC()) {
            @Override
            protected ScheduledExecutorService createScheduler() {
                return mockScheduler;
            }
        };

        service.shutdown();

        verify(mockScheduler).shutdown();
        verify(mockScheduler).awaitTermination(eq(5L), eq(TimeUnit.SECONDS));
    }

    // Вспомогательный метод для получения запланированного Runnable
    private Runnable captureRunnable(ScheduledExecutorService scheduler, int callIndex) {
        var captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeast(callIndex + 1)).scheduleAtFixedRate(
                captor.capture(), anyLong(), anyLong(), any()
        );
        return captor.getAllValues().get(callIndex);
    }
}
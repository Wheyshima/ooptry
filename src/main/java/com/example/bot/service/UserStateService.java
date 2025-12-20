package com.example.bot.service;

import com.example.bot.command.impl.TodoCommand;
import com.example.bot.database.DatabaseManager;
import com.example.bot.model.City;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserStateService {
    private static final Logger logger = LoggerFactory.getLogger(UserStateService.class);

    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> editStartTimes = new ConcurrentHashMap<>();

    private TodoCommand todoCommand;
    private final CityService cityService;
    private final DatabaseManager databaseManager;
    private final MessageSender messageSender;

    public static final long EDIT_TIMEOUT_MS = 10_000; // 10 секунд

    private final ScheduledExecutorService stateScheduler = Executors.newScheduledThreadPool(1);

    public UserStateService(CityService cityService,
                            DatabaseManager databaseManager,
                            MessageSender messageSender) {

        this.cityService = cityService;
        this.databaseManager = databaseManager;
        this.messageSender = messageSender;
    }
    public void setTodoCommand(TodoCommand todoCommand) {
        this.todoCommand = todoCommand;
    }

    public void startEditTimeoutCleanup() {
        stateScheduler.scheduleAtFixedRate(
                this::cleanupExpiredEditStates,
                1,
                1,
                TimeUnit.MINUTES
        );
        logger.debug("Запущена периодическая очистка устаревших состояний редактирования");
    }

    public void shutdown() {
        stateScheduler.shutdown();
        try {
            if (!stateScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                stateScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            stateScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // === Public API ===

    public void startTodoEditState(Long userId, int taskId) {
        userStates.put(userId, new UserState(StateType.EDITING_TODO_TASK, taskId));
        editStartTimes.put(userId, System.currentTimeMillis());
    }

    public void startCitySelectionState(Long userId) {
        userStates.put(userId, new UserState(StateType.SETTING_CITY, -1));
        editStartTimes.put(userId, System.currentTimeMillis());
    }

    public boolean hasActiveState(Long userId) {
        return userStates.containsKey(userId);
    }

    public void cancelUserState(Long userId) {
        cleanupEditState(userId);
    }

    public boolean isEditTimedOut(Long userId) {
        Long startTime = editStartTimes.get(userId);
        if (startTime == null) return true;
        return (System.currentTimeMillis() - startTime) > EDIT_TIMEOUT_MS;
    }

    public void cleanupEditState(Long userId) {
        userStates.remove(userId);
        editStartTimes.remove(userId);
    }

    public void handleUserState(Long userId, String text, Long chatId) {
        if (isCancelCommand(text)) {
            cleanupEditState(userId);
            messageSender.sendText(chatId, " Действие отменено.");
            return;
        }

        UserState state = userStates.get(userId);
        if (state == null) return;

        try {
            String response = processUserState(userId, text, state);
            messageSender.sendText(chatId, response);
        } catch (Exception e) {
            logger.error("Ошибка при обработке состояния пользователя {}", userId, e);
            cleanupEditState(userId);
            messageSender.sendText(chatId, " Произошла ошибка при обработке. Состояние сброшено.");
        }
    }

    // === Private helpers ===

    protected void cleanupExpiredEditStates() {
        long currentTime = System.currentTimeMillis();
        editStartTimes.entrySet().removeIf(entry -> {
            Long userId = entry.getKey();
            Long startTime = entry.getValue();
            if (startTime != null && (currentTime - startTime) > EDIT_TIMEOUT_MS) {
                cleanupEditState(userId);
                sendTimeoutNotification(userId);
                return true;
            }
            return false;
        });
    }

    private void sendTimeoutNotification(Long userId) {
        String message = """
            ⏰ *Время редактирования истекло*
            
            Редактирование автоматически отменено через 10 секунд бездействия.
            Попробуйте снова.""";

        messageSender.sendText(userId, message);
    }

    private boolean isCancelCommand(String text) {
        return text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel");
    }

    private String processUserState(Long userId, String text, UserState state) {
        cleanupEditState(userId); // всегда очищаем после обработки

        return switch (state.getType()) {
            case EDITING_TODO_TASK -> todoCommand.handleEditInput(userId, state.getTaskId(), text);
            case SETTING_CITY -> {
                City matchedCity = cityService.findCity(text);
                if (matchedCity != null) {
                    databaseManager.updateUserCity(userId, matchedCity.getName());
                    yield "✅ Город установлен: *" + matchedCity.getName() + "*\nрегион: " + matchedCity.getRegion();
                } else {
                    yield """
                        ❌ Город не найден.
                        Попробуйте ещё раз или используйте /setcity для выбора из списка.
                        """;
                }
            }
        };
    }

    // === Вложенные классы состояния ===

    public static class UserState {
        private final StateType type;
        private final int taskId;

        public UserState(StateType type, int taskId) {
            this.type = type;
            this.taskId = taskId;
        }

        public StateType getType() { return type; }
        public int getTaskId() { return taskId; }
    }

    public enum StateType {
        EDITING_TODO_TASK,
        SETTING_CITY
    }
}
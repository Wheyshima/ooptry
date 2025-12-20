// com.example.bot.service/UserStateService.java
package com.example.bot.service;

import com.example.bot.command.Command;
import com.example.bot.command.CommandRegistry;
import com.example.bot.command.impl.TodoCommand;
import com.example.bot.command.impl.WishlistCommand;
import com.example.bot.database.DatabaseManager;
import com.example.bot.model.City;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserStateService {
    private static final Logger logger = LoggerFactory.getLogger(UserStateService.class);

    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> editStartTimes = new ConcurrentHashMap<>();

    private final CityService cityService;
    private final DatabaseManager databaseManager;
    private final MessageSender messageSender;
    private final CommandRegistry commandRegistry;

    public static final long EDIT_TIMEOUT_MS = 10_000; // 10 секунд
    private final ScheduledExecutorService stateScheduler = Executors.newScheduledThreadPool(1);

    // ✅ КОНСТРУКТОР: НЕТ ЗАВИСИМОСТИ ОТ TodoCommand
    public UserStateService(
            CityService cityService,
            DatabaseManager databaseManager,
            MessageSender messageSender,
            CommandRegistry commandRegistry
    ) {
        this.cityService = cityService;
        this.databaseManager = databaseManager;
        this.messageSender = messageSender;
        this.commandRegistry = commandRegistry;
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
    public void startTodoAddState(Long userId) {
        userStates.put(userId, new UserState(StateType.ADDING_TODO_TASK, -1));
        editStartTimes.put(userId, System.currentTimeMillis());
    }

    public void startWishlistAddState(Long userId) {
        userStates.put(userId, new UserState(StateType.ADDING_WISHLIST_ITEM, -1));
        editStartTimes.put(userId, System.currentTimeMillis());
    }

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
        if (isCancelOrMenuCommand(text)) {
            cleanupEditState(userId);
            if (text.trim().toLowerCase().contains("меню") || text.equals("/menu")) {
                messageSender.sendTextWithKeyboard(chatId, "🏠 Вы вернулись в главное меню.", KeyboardService.mainMenu());
            } else {
                messageSender.sendText(chatId, "❌ Действие отменено.");
            }
            return;
        }

        UserState state = userStates.get(userId);
        if (state == null) return;

        try {
            String response = processUserState(userId, text, state);
            if (!response.isEmpty()) {
                messageSender.sendText(chatId, response);
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке состояния пользователя {}", userId, e);
            cleanupEditState(userId);
            messageSender.sendText(chatId, "Произошла ошибка при обработке. Состояние сброшено.");
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

    private boolean isCancelOrMenuCommand(String text) {
        String lower = text.trim().toLowerCase();
        return lower.equals("отмена") ||
                lower.equals("cancel") ||
                lower.equals("меню") ||
                lower.equals("главное меню") ||
                lower.equals("/menu");
    }

    // ✅ ВАЖНО: получаем команды через CommandRegistry
    private String processUserState(Long userId, String text, UserState state) {
        return switch (state.getType()) {
            case EDITING_TODO_TASK -> {
                cleanupEditState(userId);
                Command cmd = commandRegistry.getCommand("todo");
                if (cmd instanceof TodoCommand todoCmd) {
                    yield todoCmd.handleEditInput(userId, state.getTaskId(), text);
                } else {
                    yield "❌ Ошибка: команда /todo недоступна.";
                }
            }
            case ADDING_TODO_TASK -> {
                cleanupEditState(userId);
                Command cmd = commandRegistry.getCommand("todo");
                if (cmd instanceof TodoCommand todoCmd) {
                    yield todoCmd.handleAddTask(userId, text);
                } else {
                    yield "❌ Ошибка: команда /todo недоступна.";
                }
            }
            case ADDING_WISHLIST_ITEM -> {
                cleanupEditState(userId);
                Command cmd = commandRegistry.getCommand("wishlist");
                if (cmd instanceof WishlistCommand wishlistCmd) {
                    yield wishlistCmd.handleAddWish(userId, text);
                } else {
                    yield "❌ Ошибка: команда /wishlist недоступна.";
                }
            }
            case SETTING_CITY -> {
                City matchedCity = cityService.findCity(text);
                if (matchedCity != null) {
                    databaseManager.updateUserCity(userId, matchedCity.getName());
                    cleanupEditState(userId);
                    yield "✅ Город установлен: *" + matchedCity.getName() + "*\nрегион: " + matchedCity.getRegion() + "\nЧтобы посмотреть погоду /stats";
                } else {
                    List<City> suggestions = cityService.findCitiesFuzzy(text, 5, 65);
                    if (!suggestions.isEmpty()) {
                        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                        for (City city : suggestions) {
                            InlineKeyboardButton button = InlineKeyboardButton.builder()
                                    .text(city.getName())
                                    .callbackData("select_city_from_state:" + city.getName())
                                    .build();
                            rows.add(List.of(button));
                        }
                        InlineKeyboardButton cancelBtn = InlineKeyboardButton.builder()
                                .text("❌ Отмена")
                                .callbackData("cancel_city_selection")
                                .build();
                        rows.add(List.of(cancelBtn));

                        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                                .keyboard(rows)
                                .build();

                        messageSender.sendTextWithInlineKeyboard(
                                userId,
                                "❓ Город *\"" + text + "\"* не найден.\n\nВыберите подходящий:",
                                keyboard
                        );
                        yield "";
                    } else {
                        yield """
                            ❌ Город не найден.
                            Попробуйте ещё раз или используйте /setcity для выбора из списка.

                            Чтобы отменить — напишите *отмена*.
                            """;
                    }
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
        SETTING_CITY,
        ADDING_TODO_TASK,
        ADDING_WISHLIST_ITEM
    }
}
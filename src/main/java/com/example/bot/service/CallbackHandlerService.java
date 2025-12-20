// com.example.bot.service/CallbackHandlerService.java
package com.example.bot.service;

import com.example.bot.command.Command;
import com.example.bot.command.CommandRegistry;
import com.example.bot.database.DatabaseManager;
import com.example.bot.keyboard.InlineKeyboardFactory;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class CallbackHandlerService {
    private final DatabaseManager databaseManager;
    private final CommandRegistry commandRegistry;
    private final MessageSender messageSender;
    private final UserStateService userStateService;
    private final CityService cityService;

    public CallbackHandlerService(
            DatabaseManager databaseManager,
            CommandRegistry commandRegistry,
            MessageSender messageSender,
            UserStateService userStateService,
            CityService cityService
            // ← ДОБАВЬ ПАРАМЕТР
    ) {
        this.databaseManager = databaseManager;
        this.commandRegistry = commandRegistry;
        this.messageSender = messageSender;
        this.userStateService = userStateService;
        this.cityService = cityService;
    }

    public void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            if (data.equals("change_city_yes")) {
                showCitySelectionMenu(chatId);
            } else if (data.equals("change_city_no")) {
                messageSender.sendText(chatId, "✅ Изменение отменено.");
            } else if (data.startsWith("select_city:")) {
                handleSelectCity(chatId, userId, data.substring("select_city:".length()));
            } else if (data.equals("select_city_manual")) {
                messageSender.sendText(chatId, "Введите название города вручную (только РФ):");
                userStateService.startCitySelectionState(userId);
            } else if (data.startsWith("select_city_from_state:")) {
                handleSelectCityFromState(chatId, userId, data.substring("select_city_from_state:".length()));
            } else if (data.equals("cancel_city_selection")) {
                userStateService.cancelUserState(userId);
                messageSender.sendText(chatId, "❌ Выбор города отменён.");
            } else if (data.equals("stats:week")) {
                handleWeekStats(chatId, userId);
            } else if (data.startsWith("todo:")) {
                handleTodoCallback(data, callbackQuery);
            } else if (data.startsWith("wishlist:")) {
                handleWishlistCallback(data, callbackQuery);
            }
        } catch (Exception e) {
            messageSender.sendText(chatId, "Произошла ошибка. Попробуйте снова.");
        }
    }

    // --- City handlers ---
    private void showCitySelectionMenu(Long chatId) {
        List<String> topCities = cityService.getTop10Cities();
        List<InlineKeyboardButton> buttons = topCities.stream()
                .map(city -> InlineKeyboardButton.builder()
                        .text(city)
                        .callbackData("select_city:" + city)
                        .build())
                .toList();

        InlineKeyboardButton manualBtn = InlineKeyboardButton.builder()
                .text("✏️ Ввести вручную")
                .callbackData("select_city_manual")
                .build();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 2) {
            rows.add(buttons.subList(i, Math.min(i + 2, buttons.size())));
        }
        rows.add(List.of(manualBtn));

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();

        messageSender.sendTextWithInlineKeyboard(
                chatId,
                "Выберите город из списка или введите вручную:",
                keyboard
        );
    }

    private void handleSelectCity(Long chatId, Long userId, String cityName) {
        Message fakeMessage = createFakeMessage(chatId, userId, "/setcity " + cityName);
        executeCommand(fakeMessage, chatId);
    }

    private void handleSelectCityFromState(Long chatId, Long userId, String cityName) {
        Message fakeMessage = createFakeMessage(chatId, userId, "/setcity " + cityName);
        executeCommand(fakeMessage, chatId);
        userStateService.cancelUserState(userId);
    }

    // --- Stats handler ---
    private void handleWeekStats(Long chatId, Long userId) {
        Message fakeMessage = createFakeMessage(chatId, userId, "/stats week");
        executeCommand(fakeMessage, chatId);
    }

    // --- тodo handlers ---
    private void handleTodoCallback(String data, CallbackQuery callbackQuery) {
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();
        String action = data.substring("todo:".length());

        switch (action) {
            case "add" -> {
                messageSender.sendText(chatId, """
                    ✍️ *Добавление задачи*

                    Отправьте текст новой задачи прямо в чат.

                    Пример: _Сходить в магазин_

                    Или отправьте 'отмена' для отмены.""");
                userStateService.startTodoAddState(userId);
            }
            case "complete", "edit" -> handleTodoSelection(chatId, userId, action);
            case "refresh" -> {
                Message fakeMessage = createFakeMessage(chatId, userId, "/todo");
                String response = commandRegistry.findCommandForMessage(fakeMessage).execute(fakeMessage);
                messageSender.editMessageText(chatId, callbackQuery.getMessage().getMessageId(), response, InlineKeyboardFactory.getTodoActionsKeyboard());
            }
            case "cancel" -> messageSender.sendText(chatId, "❌ Действие отменено.");
            default -> {
                if (action.startsWith("complete:") || action.startsWith("edit:")) {
                    handleTodoAction(chatId, userId, action);
                }
            }
        }
    }

    private void handleTodoSelection(Long chatId, Long userId, String action) {
        var tasks = databaseManager.getDailyTasks(userId);
        if (tasks.isEmpty()) {
            messageSender.sendText(chatId, "📭 У вас нет задач на сегодня.");
            return;
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            var task = tasks.get(i);
            if ((action.equals("complete") && task.isCompleted()) ||
                    (action.equals("edit") && task.isCompleted())) continue;

            String text = "#%d %s".formatted(i + 1,
                    task.getText().length() > 20 ? task.getText().substring(0, 20) + "…" : task.getText());
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(text)
                    .callbackData("todo:%s:%d".formatted(action, task.getId()))
                    .build()));
        }

        if (rows.isEmpty()) {
            String msg = action.equals("edit")
                    ? "⚠️ Нет незавершённых задач для редактирования."
                    : "⚠️ Все задачи уже завершены!";
            messageSender.sendText(chatId, msg);
            return;
        }

        if ("edit".equals(action)) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text("❌ Отмена")
                    .callbackData("todo:cancel")
                    .build()));
        }

        messageSender.sendTextWithInlineKeyboard(chatId,
                action.equals("edit")
                        ? "✏️ *Выберите задачу для редактирования:*"
                        : "✅ *Выберите задачу для завершения:*",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleTodoAction(Long chatId, Long userId, String action) {
        String[] parts = action.split(":", 2);
        String act = parts[0];
        try {
            int realTaskId = Integer.parseInt(parts[1]);
            int displayIndex = getDisplayIndexByRealId(userId, realTaskId);

            if (displayIndex == -1) {
                messageSender.sendText(chatId, "❌ Задача не найдена");
                return;
            }

            System.out.println("Пользователь {} выбрал задачу #{} для редактирования (realId={})"+ userId+ displayIndex+ realTaskId);

            Message fakeMessage = createFakeMessage(chatId, userId, "/todo %s %d".formatted(act, displayIndex));
            executeCommand(fakeMessage, chatId);
        } catch (NumberFormatException e) {
            messageSender.sendText(chatId, "❌ Ошибка ID задачи.");
        }
    }

    // --- Wishlist handlers ---
    private void handleWishlistCallback(String data, CallbackQuery callbackQuery) {
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();
        String action = data.substring("wishlist:".length());

        switch (action) {
            case "add" -> handleWishlistAdd(chatId, userId);
            case "complete" -> handleWishlistCompleteSelection(chatId, userId);
            case "endadd" -> handleWishlistEndAdd(chatId, userId);
            case "refresh" -> handleWishlistRefresh(chatId, userId, callbackQuery.getMessage().getMessageId());
            case "cancel" -> messageSender.sendText(chatId, "❌ Действие отменено.");
            default -> {
                if (action.startsWith("complete:")) {
                    handleWishlistCompleteAction(chatId, userId, action);
                }
            }
        }
    }

    private void handleWishlistAdd(Long chatId, Long userId) {
        if (databaseManager.isWishlistLocked(userId)) {
            messageSender.sendText(chatId, "🔒 Добавление желаний временно заблокировано.");
            return;
        }
        messageSender.sendText(chatId, """
            ✨ *Добавление желания*
            
            Отправьте текст вашего желания в чат.
            
            _Пример: Найти своё предназначение до конца года_
            
            Чтобы отменить — напишите *отмена*.""");
        userStateService.startWishlistAddState(userId);
    }

    private void handleWishlistCompleteSelection(Long chatId, Long userId) {
        var wishes = databaseManager.getWishes(userId);
        if (wishes.isEmpty()) {
            messageSender.sendText(chatId, "📭 У вас нет желаний.");
            return;
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < wishes.size(); i++) {
            var wish = wishes.get(i);
            if (wish.isCompleted()) continue;
            String text = "#%d %s".formatted(i + 1,
                    wish.getText().length() > 25 ? wish.getText().substring(0, 25) + "…" : wish.getText());
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(text)
                    .callbackData("wishlist:complete:%d".formatted(wish.getId()))
                    .build()));
        }

        if (rows.isEmpty()) {
            messageSender.sendText(chatId, "✅ Все желания уже выполнены!");
            return;
        }

        messageSender.sendTextWithInlineKeyboard(chatId, "✅ *Выберите желание для завершения:*",
                InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void handleWishlistEndAdd(Long chatId, Long userId) {
        if (databaseManager.isWishlistLocked(userId)) {
            messageSender.sendText(chatId, "🔒 Карта уже заблокирована.");
            return;
        }
        int count = databaseManager.getWishCount(userId);
        if (count == 0) {
            messageSender.sendText(chatId, "❌ Нельзя заблокировать пустой список желаний.");
            return;
        }
        Message fakeMsg = createFakeMessage(chatId, userId, "/wishlist endadd");
        executeCommand(fakeMsg, chatId);
    }

    private void handleWishlistRefresh(Long chatId, Long userId, Integer messageId) {
        Message fakeMsg = createFakeMessage(chatId, userId, "/wishlist");
        Command cmd = commandRegistry.findCommandForMessage(fakeMsg);
        if (cmd != null) {
            String response = cmd.execute(fakeMsg);
            boolean isLocked = databaseManager.isWishlistLocked(userId);
            boolean hasWishes = !databaseManager.getWishes(userId).isEmpty();
            var keyboard = InlineKeyboardFactory.getWishlistActionsKeyboard(isLocked, hasWishes);
            messageSender.editMessageText(chatId, messageId, response, keyboard);
        }
    }

    private void handleWishlistCompleteAction(Long chatId, Long userId, String action) {
        try {
            int realWishId = Integer.parseInt(action.substring("complete:".length()));
            var wishes = databaseManager.getWishes(userId);
            int displayIndex = -1;
            for (int i = 0; i < wishes.size(); i++) {
                if (wishes.get(i).getId() == realWishId) {
                    displayIndex = i + 1;
                    break;
                }
            }
            if (displayIndex == -1) {
                messageSender.sendText(chatId, "❌ Желание не найдено.");
                return;
            }
            Message fakeMsg = createFakeMessage(chatId, userId, "/wishlist complete " + displayIndex);
            executeCommand(fakeMsg, chatId);
        } catch (NumberFormatException e) {
            messageSender.sendText(chatId, "❌ Ошибка ID желания.");
        }
    }

    // --- Вспомогательные методы ---
    private Message createFakeMessage(Long chatId, Long userId, String text) {
        Message msg = new Message();
        msg.setChat(new Chat());
        msg.getChat().setId(chatId);
        msg.setFrom(new User());
        msg.getFrom().setId(userId);
        msg.setText(text);
        return msg;
    }

    private void executeCommand(Message fakeMessage, Long chatId) {
        Command cmd = commandRegistry.findCommandForMessage(fakeMessage);
        if (cmd != null) {
            String response = cmd.execute(fakeMessage);
            messageSender.sendText(chatId, response);
        }
    }

    private int getDisplayIndexByRealId(Long userId, int realTaskId) {
        var tasks = databaseManager.getDailyTasks(userId);
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId() == realTaskId) {
                return i + 1;
            }
        }
        return -1;
    }
}
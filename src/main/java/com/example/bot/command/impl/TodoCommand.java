package com.example.bot.command.impl;

import com.example.bot.ChatBot;
import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public class TodoCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;
    private final ChatBot chatBot;

    // Константы для валидации
    private static final int MIN_TASK_LENGTH = 2;
    private static final int MAX_TASK_LENGTH = 50;

    public TodoCommand(DatabaseManager databaseManager, ChatBot chatBot) {
        super("todo", "Управление ежедневными задачами");
        this.databaseManager = databaseManager;
        this.chatBot = chatBot;
    }

    @Override
    public String execute(Message message) {
        String argument = getCommandArgument(message).trim();
        Long userId = message.getFrom().getId();

        if (chatBot.hasActiveState(userId)) {
            chatBot.cancelUserState(userId);
            return "⚠️ Предыдущее действие отменено. Обрабатываю новую команду...";
        }

        if (argument.isEmpty()) {
            return showTasks(userId);
        }

        return switch (getCommandAction(argument)) {
            case "add" -> handleAddTask(userId, getActionArgument(argument, "add"));
            case "complete" -> handleCompleteTask(userId, getActionArgument(argument, "complete"));
            case "edit" -> handleEditTask(userId, getActionArgument(argument, "edit"));
            case "stats" -> showStats(userId);
            default -> getUsage();
        };
    }

    /**
     * Определяет действие команды
     */
    private String getCommandAction(String argument) {
        if (argument.startsWith("add ")) return "add";
        if (argument.startsWith("complete ")) return "complete";
        if (argument.startsWith("edit ")) return "edit";
        if (argument.equals("stats")) return "stats";
        return "unknown";
    }

    /**
     * Извлекает аргумент действия
     */
    private String getActionArgument(String argument, String action) {
        return argument.substring(action.length()).trim();
    }

    private String handleAddTask(Long userId, String taskText) {
        if (taskText.isEmpty()) {
            return """
                ⚠️ Пожалуйста, укажите текст задачи
                Пример: `/todo add Сходить в магазин`""";
        }

        String validationError = validateTaskText(taskText);
        if (validationError != null) {
            return validationError;
        }

        int taskId = databaseManager.addDailyTask(userId, taskText);
        if (taskId != -1) {
            saveUserStats(userId);
            return buildAddTaskSuccessResponse(userId, taskText);
        }
        return "❌ Ошибка добавления задачи";
    }

    private String handleCompleteTask(Long userId, String taskIdArg) {
        try {
            int displayIndex = Integer.parseInt(taskIdArg);
            List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);

            if (displayIndex < 1 || displayIndex > tasks.size()) {
                return "❌ Неверный номер задачи. У вас всего " + tasks.size() + " задач.";
            }

            DatabaseManager.Task task = tasks.get(displayIndex - 1);
            int realTaskId = task.getId(); // ← реальный id из БД
            return completeTask(userId, realTaskId); // ← передаём реальный id
        } catch (NumberFormatException e) {
            return "❌ Неверный формат. Используйте: `/todo complete <номер>`";
        }
    }

    private String handleEditTask(Long userId, String taskIdArg) {
        try {
            int displayIndex = Integer.parseInt(taskIdArg);
            List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);

            if (displayIndex < 1 || displayIndex > tasks.size()) {
                return "❌ Неверный номер задачи. У вас всего " + tasks.size() + " задач.";
            }
            DatabaseManager.Task task = tasks.get(displayIndex - 1);
            int realTaskId = task.getId();
            return startTaskEdit(userId, realTaskId);
        } catch (NumberFormatException e) {
            return "❌ Неверный формат ID задачи. Используйте: `/todo edit <число>`";
        }
    }

    /**
     * Общий метод для проверки текста задачи
     */
    private String validateTaskText(String taskText) {
        if (taskText == null || taskText.trim().isEmpty()) {
            return "❌ Текст задачи не может быть пустым";
        }

        if (taskText.length() < MIN_TASK_LENGTH) {
            return "❌ Текст задачи слишком короткий (минимум " + MIN_TASK_LENGTH + " символа)";
        }

        if (taskText.length() > MAX_TASK_LENGTH) {
            return "❌ Текст задачи слишком длинный (максимум " + MAX_TASK_LENGTH + " символов)";
        }

        return null;
    }

    private String startTaskEdit(Long userId, int realTaskId) {
        if (chatBot.hasActiveState(userId)) {
            chatBot.cancelUserState(userId);
            return "⚠️ Предыдущее действие отменено. Начинаем новое редактирование...";
        }

        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);

        // Находим задачу и её позицию (порядковый номер)
        DatabaseManager.Task targetTask = null;
        int displayIndex = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId() == realTaskId) {
                targetTask = tasks.get(i);
                displayIndex = i + 1; // 1-based
                break;
            }
        }

        if (targetTask == null) {
            return "❌ Задача с ID " + displayIndex + " не найдена или уже истекла.\n" +
                    "Проверьте актуальный список задач: `/todo`";
        }

        if (targetTask.isCompleted()) {
            return "⚠️ Нельзя редактировать завершенную задачу #" + displayIndex + "\n" +
                    "Завершенные задачи доступны только для просмотра.";
        }

        String validationError = validateTaskText(targetTask.getText());
        if (validationError != null) {
            return validationError;
        }

        chatBot.startTodoEditState(userId, realTaskId);

        return """
            ✏️ *Редактирование задачи #%d*
            
            📝 *Текущий текст:* %s
            
            ✍️ *Введите новый текст задачи:*
            ▪ Просто напишите новый текст и отправьте сообщение
            ▪ Или отправьте 'отмена' для отмены редактирования
            """.formatted(displayIndex, targetTask.getText());
    }

    public String handleEditInput(Long userId, int realTaskId, String newText) {
        String validationError = validateTaskText(newText);
        if (validationError != null) {
            return validationError;
        }

        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
        DatabaseManager.Task targetTask = null;
        int displayIndex = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId() == realTaskId) {
                targetTask = tasks.get(i);
                displayIndex = i + 1;
                break;
            }
        }

        if (targetTask == null) {
            return "❌ Задача #"+ displayIndex +" не найдена. Возможно, она уже истекла.\nПроверьте актуальный список задач: /todo";
        }

        if (targetTask.isCompleted()) {
            return "❌ Нельзя редактировать завершенную задачу #" + displayIndex;
        }

        if (databaseManager.updateDailyTask(userId, realTaskId, newText.trim())) {
            saveUserStats(userId);
            return """
            ✅ *Задача успешно обновлена!*
            
            🔢 Номер: #%d
            📝 Новый текст: %s
            
            Посмотреть все задачи: /todo
            """.formatted(displayIndex, newText.trim());
        } else {
            return "❌ *Не удалось обновить задачу* #" + displayIndex + "\n" +
                    "Попробуйте еще раз или проверьте список задач: /todo";
        }
    }

    /**
     * Сохраняет статистику пользователя
     */
    private void saveUserStats(Long userId) {
        databaseManager.saveCurrentStats(userId);
    }

    @Override
    public String getDetailedHelp() {
        return """
            *📋 Команда /todo - Управление ежедневными задачами*
            
            *🎯 Описание:*
            Позволяет создавать и управлять задачами на текущий день.
            Задачи автоматически удаляются через 24 часа!
            
            *📝 Использование:*
            /todo - показать все задачи на сегодня
            `/todo add <текст задачи>` - добавить новую задачу
            `/todo complete <ID задачи>` - отметить задачу как выполненную
            `/todo edit <ID задачи>` - редактировать задачу
            
            *🔄 Процесс редактирования:*
            1. Введите `/todo edit <ID>`
            2. Бот запросит новый текст задачи
            3. Введите новый текст и отправьте
            4. Или отправьте "отмена" для отмены
            
            *📊 Примеры:*
            • `/todo add Сходить в магазин`
            • `/todo complete 5`
            • `/todo edit 3` - начать редактирование задачи #3
            
            *💡 Особенности:*
            • Каждая задача имеет уникальный ID
            • Задачи автоматически удаляются в 00:00
            • Отслеживается процент выполнения задач
            """;
    }

    private String showTasks(Long userId) {
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
        if (tasks.isEmpty()) {
            return "📭 На сегодня задач нет. Добавьте новую: \n`/todo add <ваша задача>`";
        }

        StringBuilder sb = new StringBuilder("*📋 Ваши задачи на сегодня:*\n\n");

        int completedCount = 0;
        int displayIndex = 1; // ← локальный счётчик
        for (DatabaseManager.Task task : tasks) {
            String status = task.isCompleted() ? "✅" : "⏳";
            sb.append("%s [#%d] %s\n".formatted(status, displayIndex, task.getText()));
            if (task.isCompleted()) completedCount++;
            displayIndex++; // ← увеличиваем только для отображения
        }

        double completionRate = databaseManager.getDailyCompletionRate(userId);
        sb.append("\n📊 *Прогресс: %d/%d задач (%.1f%%)*".formatted(
                completedCount, tasks.size(), completionRate));

        sb.append("""
            
            🔧 *Действия:*
            ✏️ Редактировать: `/todo edit <ID>`
            ✅ Завершить: `/todo complete <ID>`
            📝 Добавить: `/todo add <текст>`""");

        return sb.toString();
    }

    private String buildAddTaskSuccessResponse(Long userId, String taskText) {
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(DatabaseManager.Task::isCompleted).count();
        double completionRate = databaseManager.getDailyCompletionRate(userId);

        String taskEmoji = getTaskEmoji(totalTasks);
        String motivationMessage = getMotivationMessage(totalTasks, completedTasks);

        return """
            ✅ *Задача добавлена!* %s
            
            📝 Текст: %s
            
            📊 *Статистика за сегодня:*
            • Всего задач: %d
            • Выполнено: %d
            • Прогресс: %.1f%%
            
            %s
            
            Просмотреть все задачи: /todo
            """.formatted(taskEmoji, taskText, totalTasks, completedTasks,
                completionRate, motivationMessage);
    }

    /**
     * Возвращает эмодзи в зависимости от количества задач
     */
    private String getTaskEmoji(int taskCount) {
        return switch (taskCount) {
            case 1 -> "🎯";
            case 2, 3 -> "📝";
            case 4, 5 -> "💼";
            case 6, 7, 8 -> "🔥";
            default -> "🚀";
        };
    }

    /**
     * Возвращает мотивационное сообщение
     */
    private String getMotivationMessage(int totalTasks, int completedTasks) {
        if (totalTasks == 1) {
            return "🌟 _Отличное начало! Первый шаг к продуктивному дню._";
        }

        if (completedTasks == 0) {
            return "⏳ _Пора начинать! Выберите задачу и завершите её первой._";
        }

        double completionRatio = (double) completedTasks / totalTasks;

        if (completionRatio >= 0.8) {
            return "🎉 _Потрясающе! Вы близки к полному завершению!_";
        } else if (completionRatio >= 0.5) {
            return "💪 _Отлично! Половина пути пройдена, продолжайте в том же духе!_";
        } else if (completionRatio >= 0.25) {
            return "👏 _Хороший старт! Постепенно продвигайтесь к цели._";
        } else {
            return "🔜 _Начните с малого - завершите одну задачу для импульса._";
        }
    }

    private String completeTask(Long userId, int taskId) {
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
        boolean taskExists = tasks.stream().anyMatch(task -> task.getId() == taskId);

        if (!taskExists) {
            return "❌ Задача не найдена\n" +
                    "Проверьте актуальный список задач: `/todo`";
        }

        if (databaseManager.completeDailyTask(userId, taskId)) {
            saveUserStats(userId);
            double completionRate = databaseManager.getDailyCompletionRate(userId);
            return "✅ *Задача завершена!* 🎉\n" +
                    "📊 Общий прогресс: %.1f%%".formatted(completionRate);
        }

        return "❌ Задача не найдена или уже завершена\n" +
                "Проверьте актуальный список задач: /todo";
    }

    private String showStats(Long userId) {
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
        int completedCount = (int) tasks.stream().filter(DatabaseManager.Task::isCompleted).count();
        double completionRate = databaseManager.getDailyCompletionRate(userId);

        return """
            📊 *Статистика задач:*
            
            • Всего задач: %d
            • Выполнено: %d
            • Прогресс: %.1f%%
            """.formatted(tasks.size(), completedCount, completionRate);
    }

    private String getUsage() {
        return """
            🎯 *Управление задачами:*
            
            • /todo - показать все задачи
            • `/todo add <текст>` - добавить задачу
            • `/todo complete <ID>` - завершить задачу
            • `/todo edit <ID>` - редактировать задачу
            • `/todo stats` - статистика выполнения
            
            ⏰ Задачи автоматически удаляются в 00:00
            """;
    }
}
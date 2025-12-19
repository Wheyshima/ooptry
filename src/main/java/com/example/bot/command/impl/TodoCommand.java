package com.example.bot.command.impl;

import com.example.bot.ChatBot;
import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public class TodoCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;
    private final ChatBot chatBot;

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

        if (argument.startsWith("add ")) {
            String task = argument.substring(4).trim();

            // Сначала проверяем пустоту
            if (task.isEmpty()) {
                return "❌ Текст задачи не может быть пустым";
            }

            // Затем проверяем длину
            if (task.length() < 2) {
                return "❌ Текст задачи слишком короткий (минимум 2 символа)";
            }

            if (task.length() > 500) {
                return "❌ Текст задачи слишком длинный (максимум 500 символов)";
            }

            return addTask(userId, task);
        }

        if (argument.startsWith("complete ")) {
            try {
                int taskId = Integer.parseInt(argument.substring(9).trim());
                return completeTask(userId, taskId);
            } catch (NumberFormatException e) {
                return "❌ Неверный формат ID задачи. Используйте: `/todo complete <число>`";
            }
        }

        if (argument.startsWith("edit ")) {
            try {
                int taskId = Integer.parseInt(argument.substring(5).trim());
                return startTaskEdit(userId, taskId);
            } catch (NumberFormatException e) {
                return "❌ Неверный формат ID задачи. Используйте: `/todo edit <число>`";
            }
        }

        if (argument.equals("stats")) {
            return showStats(userId);
        }

        return getUsage();
    }

    private String startTaskEdit(Long userId, int taskId) {
        if (chatBot.hasActiveState(userId)) {
            chatBot.cancelUserState(userId);
            return "⚠️ Предыдущее действие отменено. Начинаем новое редактирование...";
        }

        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
        DatabaseManager.Task targetTask = tasks.stream()
                .filter(task -> task.getId() == taskId)
                .findFirst()
                .orElse(null);

        if (targetTask == null) {
            return "❌ Задача с ID " + taskId + " не найдена или уже истекла.\n" +
                    "Проверьте актуальный список задач: `/todo`";
        }

        if (targetTask.isCompleted()) {
            return "⚠️ Нельзя редактировать завершенную задачу #" + taskId + "\n" +
                    "Завершенные задачи доступны только для просмотра.";
        }

        chatBot.startTodoEditState(userId, taskId);

        return "✏️ *Редактирование задачи #" + taskId + "*\n\n" +
                "📝 *Текущий текст:* " + targetTask.getText() + "\n\n" +
                "✍️ *Введите новый текст задачи:*\n" +
                "▪ Просто напишите новый текст и отправьте сообщение\n" +
                "▪ Или отправьте 'отмена' для отмены редактирования";
    }

    public String handleEditInput(Long userId, int taskId, String newText) {
        if (newText.trim().isEmpty()) {
            return "⚠️ Текст задачи не может быть пустым. Редактирование отменено.";
        }

        if (newText.length() > 500) {
            return "❌ Текст задачи слишком длинный (максимум 500 символов). Редактирование отменено.";
        }

        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
        DatabaseManager.Task targetTask = tasks.stream()
                .filter(task -> task.getId() == taskId)
                .findFirst()
                .orElse(null);

        if (targetTask == null) {
            return "❌ Задача #" + taskId + " не найдена. Возможно, она уже истекла.\n" +
                    "Проверьте актуальный список задач: `/todo`";
        }

        if (targetTask.isCompleted()) {
            return "❌ Нельзя редактировать завершенную задачу #" + taskId;
        }

        if (databaseManager.updateDailyTask(userId, taskId, newText.trim())) {
            return "✅ *Задача успешно обновлена!*\n\n" +
                    "🔢 ID: #" + taskId + "\n" +
                    "📝 Новый текст: " + newText.trim() + "\n\n" +
                    "Посмотреть все задачи: `/todo`";
        } else {
            return "❌ *Не удалось обновить задачу* #" + taskId + "\n" +
                    "Попробуйте еще раз или проверьте список задач: `/todo`";
        }
    }

    @Override
    public String getDetailedHelp() {
        return """
            *📋 Команда /todo - Управление ежедневными задачами*
            
            *🎯 Описание:*
            Позволяет создавать и управлять задачами на текущий день.
            Задачи автоматически удаляются через 24 часа!
            
            *📝 Использование:*
            `/todo` - показать все задачи на сегодня
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
        for (DatabaseManager.Task task : tasks) {
            String status = task.isCompleted() ? "✅" : "⏳";
            sb.append(String.format("%s [#%d] %s\n", status, task.getId(), task.getText()));
            if (task.isCompleted()) completedCount++;
        }

        double completionRate = databaseManager.getDailyCompletionRate(userId);
        sb.append(String.format("\n📊 *Прогресс: %d/%d задач (%.1f%%)*",
                completedCount, tasks.size(), completionRate));

        sb.append("\n\n🔧 *Действия:*");
        sb.append("\n✏️ Редактировать: `/todo edit <ID>`");
        sb.append("\n✅ Завершить: `/todo complete <ID>`");
        sb.append("\n📝 Добавить: `/todo add <текст>`");

        return sb.toString();
    }

    private String addTask(Long userId, String task) {
        if (task.isEmpty()) {
            return "⚠️ Пожалуйста, укажите текст задачи\n" +
                    "Пример: `/todo add Сходить в магазин`";
        }

        int taskId = databaseManager.addDailyTask(userId, task);
        if (taskId != -1) {
            // Получаем расширенную статистику
            List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
            int totalTasks = tasks.size();
            int completedTasks = (int) tasks.stream().filter(DatabaseManager.Task::isCompleted).count();
            double completionRate = databaseManager.getDailyCompletionRate(userId);

            // Определяем эмодзи в зависимости от количества задач
            String taskEmoji = getTaskEmoji(totalTasks);
            String motivationMessage = getMotivationMessage(totalTasks, completedTasks);

            return "✅ *Задача добавлена!* " + taskEmoji + "\n\n" +
                    "🔢 ID: #" + taskId + "\n" +
                    "📝 Текст: " + task + "\n\n" +
                    "📊 *Статистика за сегодня:*\n" +
                    "• Всего задач: " + totalTasks + "\n" +
                    "• Выполнено: " + completedTasks + "\n" +
                    "• Прогресс: " + String.format("%.1f%%", completionRate) + "\n\n" +
                    motivationMessage + "\n\n" +
                    "Просмотреть все задачи: `/todo`";
        }
        return "❌ Ошибка добавления задачи";
    }

    /**
     * Возвращает эмодзи в зависимости от количества задач
     */
    private String getTaskEmoji(int taskCount) {
        if (taskCount == 1) return "🎯";
        if (taskCount <= 3) return "📝";
        if (taskCount <= 5) return "💼";
        if (taskCount <= 8) return "🔥";
        return "🚀";
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
            return "❌ Задача #" + taskId + " не найдена\n" +
                    "Проверьте актуальный список задач: `/todo`";
        }

        if (databaseManager.completeDailyTask(userId, taskId)) {
            double completionRate = databaseManager.getDailyCompletionRate(userId);
            return "✅ *Задача #" + taskId + " завершена!* 🎉\n" +
                    String.format("📊 Общий прогресс: %.1f%%", completionRate);
        }

        return "❌ Задача #" + taskId + " не найдена или уже завершена\n" +
                "Проверьте актуальный список задач: `/todo`";
    }

    private String showStats(Long userId) {
        List<DatabaseManager.Task> tasks = databaseManager.getDailyTasks(userId);
        int completedCount = (int) tasks.stream().filter(DatabaseManager.Task::isCompleted).count();
        double completionRate = databaseManager.getDailyCompletionRate(userId);

        return String.format("📊 *Статистика задач:*\n\n" +
                        "• Всего задач: %d\n" +
                        "• Выполнено: %d\n" +
                        "• Прогресс: %.1f%%",
                tasks.size(), completedCount, completionRate);
    }

    private String getUsage() {
        return """
            🎯 *Управление задачами:*
            
            • `/todo` - показать все задачи
            • `/todo add <текст>` - добавить задачу
            • `/todo complete <ID>` - завершить задачу
            • `/todo edit <ID>` - редактировать задачу
            • `/todo stats` - статистика выполнения
            
            ⏰ Задачи автоматически удаляются через 24 часа
            """;
    }
}
package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public class StatsCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;

    public StatsCommand(DatabaseManager databaseManager) {
        super("stats", "Показать статистику выполнения");
        this.databaseManager = databaseManager;
    }

    @Override
    public String getDetailedHelp() {
        return """
        *📈 Команда /stats - Статистика продуктивности*
        
        *🎯 Описание:*
        Показывает вашу статистику выполнения задач.
        
        *📝 Использование:*
        `/stats` - статистика за сегодня
        `/stats week` - статистика за неделю
        
        *📊 Что показывает:*
        • Текущий прогресс за сегодня
        • город (если он установлен)
        • Сохраненную статистику
        • Среднюю продуктивность за неделю
        • Мотивационные сообщения
        """;
    }

    @Override
    public String execute(Message message) {
        Long userId = message.getFrom().getId();
        String argument = getCommandArgument(message).trim().toLowerCase();

        // Сохраняем текущую статистику перед показом
        saveCurrentStats(userId);

        if (argument.equals("week") || argument.equals("неделя")) {
            return showWeeklyStats(userId);
        } else {
            return showTodayStats(userId);
        }
    }

    private String showTodayStats(Long userId) {
        double currentCompletionRate = databaseManager.getDailyCompletionRate(userId);
        Double savedCompletionRate = databaseManager.getTodayStats(userId);
        String city = databaseManager.getUserCity(userId);

        StringBuilder sb = new StringBuilder("*📊 Статистика за сегодня:*\n\n");

        // Добавляем город если установлен
        if (city != null && !city.trim().isEmpty()) {
            sb.append(String.format("🏙️ *Город:* %s\n", city));
        }

        // Получаем текущие задачи для отображения счетчика
        var tasks = databaseManager.getDailyTasks(userId);
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(task -> task.isCompleted()).count();

        sb.append(String.format("✅ *Выполнено:* %d/%d задач\n", completedTasks, totalTasks));
        sb.append(String.format("📈 *Продуктивность:* %.1f%%\n", currentCompletionRate));

        // Показываем сохраненную статистику если она есть
        if (savedCompletionRate != null) {
            sb.append(String.format("💾 *Сохраненная:* %.1f%%\n\n", savedCompletionRate));
        } else {
            sb.append("\n");
        }

        sb.append(getMotivationalMessage(currentCompletionRate));

        return sb.toString();
    }

    private String showWeeklyStats(Long userId) {
        List<Double> weeklyStats = databaseManager.getWeeklyStats(userId);
        String city = databaseManager.getUserCity(userId);

        if (weeklyStats.isEmpty()) {
            return "📊 *Статистика за неделю:*\n\nНет данных за последние 7 дней";
        }

        StringBuilder sb = new StringBuilder("*📊 Статистика за неделю:*\n\n");
        // Добавляем город если установлен
        if (city != null && !city.trim().isEmpty()) {
            sb.append(String.format("🏙️ *Город:* %s\n\n", city));
        }

        // Рассчитываем среднюю продуктивность
        double avgCompletion = weeklyStats.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        int activeDays = weeklyStats.size();

        sb.append(String.format("📅 *Активных дней:* %d/7\n", activeDays));
        sb.append(String.format("📈 *Средняя продуктивность:* %.1f%%\n\n", avgCompletion));

        // Показываем прогресс-бар недели
        sb.append(getWeeklyProgressBar(weeklyStats));
        sb.append("\n");

        sb.append(getWeeklyMotivationalMessage(avgCompletion));

        return sb.toString();
    }

    /**
     * Создает текстовый прогресс-бар для недельной статистики
     */
    private String getWeeklyProgressBar(List<Double> weeklyStats) {
        StringBuilder progressBar = new StringBuilder();
        String[] dayNames = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

        progressBar.append("📅 *Прогресс за неделю:*\n");

        for (int i = 0; i < Math.min(7, weeklyStats.size()); i++) {
            double completion = weeklyStats.get(i);
            String emoji = getCompletionEmoji(completion);
            progressBar.append(String.format("%s %s: %.1f%%\n", emoji, dayNames[i], completion));
        }

        return progressBar.toString();
    }

    /**
     * Возвращает эмодзи в зависимости от процента выполнения
     */
    private String getCompletionEmoji(double completion) {
        if (completion == 100) return "🟢";
        else if (completion >= 80) return "🟡";
        else if (completion >= 50) return "🟠";
        else if (completion > 0) return "🔴";
        else return "⚫";
    }

    /**
     * Сохраняет текущую статистику в базу данных
     */
    private void saveCurrentStats(Long userId) {
        try {
            double completionRate = databaseManager.getDailyCompletionRate(userId);
            // Сохраняем только если есть задачи (не NaN)
            if (!Double.isNaN(completionRate)) {
                databaseManager.saveProductivityStats(userId, completionRate);
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка при сохранении статистики для пользователя " + userId + ": " + e.getMessage());
        }
    }

    private String getMotivationalMessage(double completionRate) {
        if (completionRate == 100) {
            return "🎉 Идеальный результат! Ты просто суперзвезда! 🌟";
        } else if (completionRate >= 80) {
            return "⚡ Отличная работа! Вы на правильном пути!";
        } else if (completionRate >= 50) {
            return "💪 Хороший темп! Продолжайте в том же духе!";
        } else if (completionRate > 0) {
            return "🔥 Вы начали - это уже победа! Двигайтесь дальше!";
        } else {
            return "🎯 Начните с добавления задач: /todo add <задача>";
        }
    }

    private String getWeeklyMotivationalMessage(double avgCompletion) {
        if (avgCompletion >= 80) {
            return "🏆 Невероятная неделя! Вы продуктивны как никогда!";
        } else if (avgCompletion >= 60) {
            return "📈 Стабильный прогресс! Так держать!";
        } else if (avgCompletion >= 40) {
            return "💪 Хорошие результаты! Продолжайте в том же духе!";
        } else {
            return "🌱 Начинается новая неделя - отличный шанс улучшить результаты!";
        }
    }
}
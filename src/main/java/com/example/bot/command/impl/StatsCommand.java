package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import com.example.bot.service.WeatherService;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.time.LocalDate;

public class StatsCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;
    private final WeatherService weatherService;

    public StatsCommand(DatabaseManager databaseManager,WeatherService weatherService) {
        super("stats", "Показать статистику выполнения");
        this.databaseManager = databaseManager;
        this.weatherService = weatherService;
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
        • Город (если он установлен)
        • Сохраненную статистику
        • Среднюю продуктивность за неделю
        • Детальную статистику по дням
        • Мотивационные сообщения
        """;
    }

    @Override
    public String execute(Message message) {
        Long userId = message.getFrom().getId();
        String argument = getCommandArgument(message).trim().toLowerCase();

        return switch (argument) {
            case "week", "неделя" -> showWeeklyStats(userId);
            case "" -> showTodayStats(userId); // Пустой аргумент - статистика за сегодня
            default -> """
            ❓ *Неизвестный параметр:* '%s'
            
            %s
            
            💡 *Доступные варианты:*
            `/stats` - статистика за сегодня
            `/stats week` - статистика за неделю
            """.formatted(argument, showTodayStats(userId)); // Неизвестный аргумент - показываем помощь
        };
    }

    private String showTodayStats(Long userId) {
        double currentCompletionRate = databaseManager.getDailyCompletionRate(userId);
        Double savedCompletionRate = databaseManager.getTodayStats(userId);
        String city = databaseManager.getUserCity(userId);

        StringBuilder sb = new StringBuilder("*📊 Статистика за сегодня:*\n\n");
        // Добавляем город если установлен
        if (isValidCity(city)) {
            sb.append("🏙️ *Город:* ").append(city).append("\n");
            String weather = weatherService.getTodayForecast(city);
            sb.append("🌤️ *Погода:*\n").append(weather).append("\n\n");
        } else {
            sb.append("💡 Установите город: `/setcity Москва`\n\n");
        }
        // Получаем текущие задачи для отображения счетчика
        var tasks = databaseManager.getDailyTasks(userId);
        int totalTasks = tasks.size();
        int completedTasks = (int) tasks.stream().filter(DatabaseManager.Task::isCompleted).count();

        // Если задач нет, но есть сохраненная статистика - показываем её
        if (totalTasks == 0 && savedCompletionRate != null) {
            sb.append("""
                ✅ *Выполнено:* 0/0 задач
                📈 *Сохраненная продуктивность:* %.1f%%
                💡 *Задачи очищены, прогресс сохранен*
                
                """.formatted(savedCompletionRate));
        } else {
            // Есть задачи - показываем текущий прогресс
            sb.append("""
                ✅ *Выполнено:* %d/%d задач
                📈 *Продуктивность:* %.1f%%
                """.formatted(completedTasks, totalTasks, currentCompletionRate));

            // И сохраняем статистику только если есть задачи
            if (!Double.isNaN(currentCompletionRate)) {
                databaseManager.saveProductivityStats(userId, completedTasks, totalTasks);
            }
        }

        // Показываем сохраненную статистику если она есть
        if (savedCompletionRate != null) {
            sb.append("💾 *Сохраненная:* %.1f%%\n\n".formatted(savedCompletionRate));
        } else {
            sb.append("\n");
        }

        // Используем сохраненную статистику для мотивационного сообщения если задач нет
        double motivationRate = (totalTasks == 0 && savedCompletionRate != null) ? savedCompletionRate : currentCompletionRate;
        sb.append(getMotivationalMessage(motivationRate));

        return sb.toString();
    }

    private String showWeeklyStats(Long userId) {
        // Получаем статистику ТОЛЬКО за текущую календарную неделю (Пн–Вс)
        List<DatabaseManager.ProductivityStat> weeklyStats = databaseManager.getWeeklyProductivityStats(userId);
        String city = databaseManager.getUserCity(userId);

        if (weeklyStats.isEmpty()) {
            return """
                📊 *Статистика за неделю:*
                
                Нет данных за текущую неделю (понедельник–воскресенье)
                
                💡 *Совет:* Добавьте задачи с помощью `/todo add` и завершите день — статистика сохранится автоматически!
                """;
        }

        StringBuilder sb = new StringBuilder("*📊 Статистика за неделю:*\n\n");

        // Город
        if (isValidCity(city)) {
            sb.append("🏙️ *Город:* ").append(city).append("\n");
            String weather = weatherService.getTodayForecast(city);
            sb.append("🌤️ *Погода сегодня:*\n").append(weather).append("\n\n");
        }

        // Средняя продуктивность
        double avgCompletion = weeklyStats.stream()
                .mapToDouble(DatabaseManager.ProductivityStat::getCompletionRate)
                .average()
                .orElse(0.0);

        int activeDays = weeklyStats.size();
        sb.append("""
            📅 *Активных дней:* %d/7
            📈 *Средняя продуктивность:* %.1f%%
            
            """.formatted(activeDays, avgCompletion));

        // Детальная статистика по дням
        sb.append(getDetailedWeeklyStatsFromProductivity(weeklyStats));
        sb.append("\n");
        sb.append(getWeeklyMotivationalMessage(avgCompletion));

        return sb.toString();
    }

    private String getDetailedWeeklyStatsFromProductivity(List<DatabaseManager.ProductivityStat> stats) {
        StringBuilder sb = new StringBuilder("📋 *Детальная статистика по дням:*\n\n");

        String[] dayNames = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};

        for (DatabaseManager.ProductivityStat stat : stats) {
            LocalDate date = stat.getStatDate();
            String dayName = dayNames[date.getDayOfWeek().getValue() - 1];
            String emoji = getCompletionEmoji(stat.getCompletionRate());

            sb.append(String.format("%s *%s* (%.1f%%)\n", emoji, dayName, stat.getCompletionRate()));
            sb.append(String.format("   📝 Задач: %d/%d выполнено\n", stat.getCompletedTasks(), stat.getTotalTasks()));
            sb.append(String.format("   📅 Дата: %s\n\n", date));
        }

        // Диапазон недели
        if (!stats.isEmpty()) {
            LocalDate weekStart = stats.getFirst().getStatDate().minusDays(stats.getFirst().getStatDate().getDayOfWeek().getValue() - 1);
            LocalDate weekEnd = weekStart.plusDays(6);
            sb.append(String.format("🗓️ *Неделя: %s – %s*\n", weekStart, weekEnd));
        }

        return sb.toString();
    }

    /**
     * Проверяет валидность города
     */
    private boolean isValidCity(String city) {
        return city != null && !city.trim().isEmpty();
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
            return "🎯 Начните с добавления задач: `/todo add <задача>`";
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
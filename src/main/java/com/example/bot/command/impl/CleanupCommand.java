package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import org.telegram.telegrambots.meta.api.objects.Message;

public class CleanupCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;

    public CleanupCommand(DatabaseManager databaseManager) {
        super("cleanup", "Принудительная очистка задач");
        this.databaseManager = databaseManager;
    }

    @Override
    public String execute(Message message) {
        // Только для разработки (замените 1452874352L на ваш user_id)
        if (!message.getFrom().getId().equals(1452874352L)) {
            return "❌ Эта команда только для разработки";
        }

        new Thread(() -> {
            try {
                System.out.println("🧹 Ручной запуск очистки to do задач...");
                databaseManager.cleanupAllDailyTasks();
                System.out.println("✅ Ручная очистка завершена");
            } catch (Exception e) {
                System.err.println("❌ Ошибка при ручной очистке: " + e.getMessage());
            }
        }).start();

        return "🧹 Запущена принудительная очистка всех to do задач!";
    }
}
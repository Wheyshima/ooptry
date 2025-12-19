package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import org.telegram.telegrambots.meta.api.objects.Message;

public class ResetWishlistCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;

    public ResetWishlistCommand(DatabaseManager databaseManager) {
        super("reset_wishlist", "Полный сброс вишлиста");
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
                System.out.println("🔄 Ручной сброс вишлиста...");
                databaseManager.resetWishlist();
                System.out.println("✅ Вишлист сброшен: очищены товары, сброшены ID и блокировка");
            } catch (Exception e) {
                System.err.println("❌ Ошибка при сбросе вишлиста: " + e.getMessage());
            }
        }).start();

        return "🔄 Запущен полный сброс вишлиста! Очистка товаров, сброс ID и блокировки.";
    }
}
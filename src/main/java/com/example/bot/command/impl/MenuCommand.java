package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import org.telegram.telegrambots.meta.api.objects.Message;

public class MenuCommand extends AbstractCommand {

    public MenuCommand() {
        super("menu", "Вернуться в главное меню");
    }

    @Override
    public String execute(Message message) {
        // Само сообщение отправляется через sendTextWithKeyboard в ChatBot
        return "🏠 Главное меню";
    }

    @Override
    public String getDetailedHelp() {
        return "Команда `/menu` возвращает вас в главное меню и отменяет текущее действие.";
    }
}
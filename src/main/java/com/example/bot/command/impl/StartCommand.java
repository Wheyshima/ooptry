package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import org.telegram.telegrambots.meta.api.objects.Message;

public class StartCommand extends AbstractCommand {

    public StartCommand() {
        super("start", "Запуск бота");
    }

    @Override
    public String execute(Message message) {
        return "Привет! Я *Умный помощник для заметок и стареющих людей как вы*\n" +
                "used /help для просмотра доступных команд.";
    }
}
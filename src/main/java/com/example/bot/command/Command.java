package com.example.bot.command;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

public interface Command {
    String execute(Message message);
    BotCommand getBotCommand();
    String getDescription();
    boolean canExecute(Message message);
}
package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import org.telegram.telegrambots.meta.api.objects.Message;

public class AuthorsCommand extends AbstractCommand {

    public AuthorsCommand() {
        super("authors", "Information");
    }

    @Override
    public String execute(Message message) {
        return "*Авторы проекта*\n" +
                "harumiRui\n" +
                "angrycoke\n";
    }
}
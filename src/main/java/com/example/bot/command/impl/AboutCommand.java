package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import org.telegram.telegrambots.meta.api.objects.Message;

public class AboutCommand extends AbstractCommand {

    public AboutCommand() {
        super("about", "Про МЕНЯ");
    }

    @Override
    public String execute(Message message) {
        return "*Information о боте*\n\n" +
                "I ваше спасение от деменции\n" +
                "Save your thinks и идеи планы на день в виде заметок\n" +
                "а I как самый лучший и надежный бот запомню их и выведу вам \n" +
                "которого еще нет на гит Hab \n" +
                "I поддерживаю вас морально но и напоминаю о старении\n\n" +
                "Hola";
    }
}
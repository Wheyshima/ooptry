package com.example.bot.command;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

public abstract class AbstractCommand implements Command {
    private final BotCommand botCommand;
    private final String description;

    public AbstractCommand(String command, String description) {
        this.botCommand = new BotCommand(command, description);
        this.description = description;
    }

    @Override
    public BotCommand getBotCommand() {
        return botCommand;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getDetailedHelp() {
        // Базовая реализация - можно переопределить в дочерних классах
        return String.format(
                "*Команда /%s*\n" +
                        "*Описание:* %s\n" +
                        "*Быстрый доступ:* Напишите /%s в чат",
                botCommand.getCommand(),
                description,
                botCommand.getCommand()
        );
    }

    @Override
    public boolean canExecute(Message message) {
        return message != null &&
                message.hasText() &&
                message.getText().startsWith("/" + botCommand.getCommand());
    }

    protected String getCommandArgument(Message message) {
        String text = message.getText();
        String command = "/" + botCommand.getCommand();
        if (text.startsWith(command)) {
            return text.substring(command.length()).trim();
        }
        return "";
    }
}
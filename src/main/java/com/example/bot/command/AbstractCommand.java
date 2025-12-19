package com.example.bot.command;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

public abstract class AbstractCommand implements Command {
    protected final String command;
    protected final String description;

    public AbstractCommand(String command, String description) {
        this.command = command;
        this.description = description;
    }

    public String getCommandIdentifier() {
        return command;
    }

    @Override
    public BotCommand getBotCommand() {
        return new BotCommand(command, description);
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean canExecute(Message message) {
        return message.hasText() && message.getText().startsWith("/" + command);
    }

    protected String getCommandArgument(Message message) {
        String text = message.getText();
        String[] parts = text.split(" ", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}
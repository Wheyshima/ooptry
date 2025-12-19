package com.example.bot.command;

import java.util.*;

public class CommandRegistry {
    private final Map<String, Command> commands = new HashMap<>();
    private final List<Command> commandList = new ArrayList<>();

    public void registerCommand(Command command) {
        String commandName = command.getBotCommand().getCommand();
        commands.put(commandName, command);
        commandList.add(command);
    }

    public Command getCommand(String commandName) {
        return commands.get(commandName);
    }

    public List<Command> getAllCommands() {
        return new ArrayList<>(commandList);
    }

    public List<org.telegram.telegrambots.meta.api.objects.commands.BotCommand> getBotCommands() {
        List<org.telegram.telegrambots.meta.api.objects.commands.BotCommand> botCommands = new ArrayList<>();
        for (Command command : commandList) {
            botCommands.add(command.getBotCommand());
        }
        return botCommands;
    }

    public Command findCommandForMessage(org.telegram.telegrambots.meta.api.objects.Message message) {
        for (Command command : commandList) {
            if (command.canExecute(message)) {
                return command;
            }
        }
        return null;
    }
}
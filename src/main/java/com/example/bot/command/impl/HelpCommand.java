package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import com.example.bot.command.Command;
import com.example.bot.command.CommandRegistry;
import org.telegram.telegrambots.meta.api.objects.Message;

public class HelpCommand extends AbstractCommand {
    private final CommandRegistry commandRegistry;

    public HelpCommand(CommandRegistry commandRegistry) {
        super("help", "Помощь по command бота");
        this.commandRegistry = commandRegistry;
    }

    @Override
    public String execute(Message message) {
        String argument = getCommandArgument(message).trim();

        if (!argument.isEmpty()) {
            return getSpecificHelp(argument);
        } else {
            return getAllCommandsHelp();
        }
    }

    private String getAllCommandsHelp() {
        StringBuilder helpText = new StringBuilder();
        helpText.append("*Доступные команды:*\n\n");

        for (Command command : commandRegistry.getAllCommands()) {
            helpText.append("/")
                    .append(command.getBotCommand().getCommand())
                    .append(" - ")
                    .append(command.getDescription())
                    .append("\n");
        }

        helpText.append("\nДля получения подробной информации о команде используйте: /help <команда>");
        return helpText.toString();
    }

    private String getSpecificHelp(String commandName) {
        if (commandName.startsWith("/")) {
            commandName = commandName.substring(1);
        }

        Command command = commandRegistry.getCommand(commandName);
        if (command != null) {
            return String.format("*Команда /%s*\n\n%s",
                    commandName,
                    command.getDescription());
        } else {
            return "Команда \"/" + commandName + "\" *не найдена*.\n" +
                    "Используйте /help для просмотра всех доступных команд.";
        }
    }
}
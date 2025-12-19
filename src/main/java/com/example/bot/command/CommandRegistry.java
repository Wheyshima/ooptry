package com.example.bot.command;

import java.util.*;

/**
 * Реестр для управления командами бота
 * использует LinkedHashMap для сохранения порядка добавления и быстрого поиска
 */
public class CommandRegistry {
    private final LinkedHashMap<String, Command> commands = new LinkedHashMap<>();

    /**
     * Регистрирует новую команду
     * @param command команда для регистрации
     */
    public void registerCommand(Command command) {
        String commandName = command.getBotCommand().getCommand();
        commands.put(commandName, command);
    }

    /**
     * Возвращает команду по имени
     * @param commandName имя команды (без слэша)
     * @return команда или null если не найдена
     */
    public Command getCommand(String commandName) {
        return commands.get(commandName);
    }

    /**
     * Возвращает список всех зарегистрированных команд в порядке регистрации
     * @return список команд
     */
    public List<Command> getAllCommands() {
        return new ArrayList<>(commands.values());
    }

    /**
     * Возвращает команды для регистрации в меню бота
     * @return список BotCommand
     */
    public List<org.telegram.telegrambots.meta.api.objects.commands.BotCommand> getBotCommands() {
        List<org.telegram.telegrambots.meta.api.objects.commands.BotCommand> botCommands = new ArrayList<>();
        for (Command command : commands.values()) {
            botCommands.add(command.getBotCommand());
        }
        return botCommands;
    }

    /**
     * Находит команду, которая может обработать сообщение
     * @param message сообщение от пользователя
     * @return команда или null если не найдена подходящая
     */
    public Command findCommandForMessage(org.telegram.telegrambots.meta.api.objects.Message message) {
        if (!message.hasText()) {
            return null;
        }

        String text = message.getText();
        if (!text.startsWith("/")) {
            return null;
        }

        // Извлекаем имя команды (убираем / и аргументы)
        String[] parts = text.substring(1).split("\\s+", 2);
        String commandName = parts[0].toLowerCase();

        return commands.get(commandName);
    }

    /**
     * Возвращает количество зарегистрированных команд
     * @return количество команд
     */
    public int getCommandCount() {
        return commands.size();
    }

    /**
     * Проверяет, зарегистрирована ли команда
     * @param commandName имя команды
     * @return true если команда существует
     */
    public boolean containsCommand(String commandName) {
        return commands.containsKey(commandName);
    }
}
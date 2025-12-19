package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import org.telegram.telegrambots.meta.api.objects.Message;

public class SetCityCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;

    public SetCityCommand(DatabaseManager databaseManager) {
        super("setcity", "локация для определения погоды");
        this.databaseManager = databaseManager;
    }

    @Override
    public String getDetailedHelp() {
        return """
        *🏙 Команда /setcity - Установка вашего города*
        
        *🎯 Описание:*
        Сохраняет ваш город для персонализации статистики и будущих функций.
        
        *📝 Использование:*
        `/setcity <название города>` - установить город
        
        *📊 Примеры:*
        • `/setcity Москва`
        
        *💡 Особенности:*
        • Город сохраняется в вашем профиле
        • Используется для персонализации
        • Можно изменить в любой момент
        """;
    }

    @Override
    public String execute(Message message) {
        String argument = getCommandArgument(message).trim();
        if (argument.isEmpty()) {
            return "Please, укажите город. Пример: `/setcity <ваш город>`";
        }

        Long userId = message.getFrom().getId();
        databaseManager.updateUserCity(userId, argument);

        return String.format("Город успешно установлен: %s", argument);
    }
}
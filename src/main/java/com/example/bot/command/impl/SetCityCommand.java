package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import com.example.bot.model.City;
import com.example.bot.service.CityService;
import org.telegram.telegrambots.meta.api.objects.Message;

public class SetCityCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;
    private final CityService cityService;

    public SetCityCommand(DatabaseManager databaseManager, CityService cityService) {
        super("setcity", "установить или посмотреть ваш город в России");
        this.databaseManager = databaseManager;
        this.cityService = cityService;
    }

    @Override
    public String getDetailedHelp() {
        return """
        *🏙 Команда /setcity - Установка или просмотр вашего города*
        
        *🎯 Описание:*
        — Если вызвать без параметра → покажет текущий город.
        — Если указать название → установит новый город (только РФ).
        
        *📝 Использование:*
        `/setcity` — посмотреть текущий город
        `/setcity <название>` — установить новый город
        
        *📊 Примеры:*
        • `/setcity`
        • `/setcity Москва`
        • `/setcity Санкт-Петербург`
        
        *💡 Особенности:*
        • Поддерживается нечёткий поиск (опечатки, регистр)
        • Работает только с городами России
        • Город можно изменить в любой момент
        """;
    }

    @Override
    public String execute(Message message) {
        String rawInput = getCommandArgument(message).trim();
        Long userId = message.getFrom().getId();

        // Если аргумента нет — показываем текущий город (если есть)
        if (rawInput.isEmpty()) {
            String currentCity = databaseManager.getUserCity(userId);
            if (currentCity != null && !currentCity.isBlank()) {
                return String.format(
                        "Ваш текущий город: *%s*\nЧтобы изменить, используйте:\n`/setcity <новый город>`",
                        currentCity
                );
            } else {
                return """
                У вас пока не установлен город.
                
                Укажите город, чтобы включить персонализацию:
                `/setcity Москва`
                """;
            }
        }

        // Если аргумент есть — пытаемся установить новый город
        City matchedCity = cityService.findCity(rawInput);

        if (matchedCity == null) {
            return """
            ❌ Город не найден в России.
            
            Убедитесь, что:
            • Название написано правильно
            • Город существует в РФ
            
            Пример: `/setcity Новосибирск`
            
            Если все же город не установился и вы знаете что он существует:
            -> Введите ближайщий к вам город входящий в топ 100 по населению
            """;
        }

        // Сохраняем нормализованное название
        databaseManager.updateUserCity(userId, matchedCity.getName());

        return String.format(
                "✅ Город успешно установлен:\n*%s*\nрегион: %s",
                matchedCity.getName(),
                matchedCity.getRegion()
        );
    }
}
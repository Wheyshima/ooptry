package com.example.bot.service;

import com.example.bot.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MorningNewsletterService {
    private static final Logger logger = LoggerFactory.getLogger(MorningNewsletterService.class);

    private final DatabaseManager databaseManager;
    private final AbsSender bot;
    private final WeatherService weatherService;
    private final DailyTarotService tarotService;

    public MorningNewsletterService(DatabaseManager databaseManager, AbsSender bot, String openWeatherApiKey) {
        this.databaseManager = databaseManager;
        this.bot = bot;
        this.weatherService = new WeatherService(openWeatherApiKey); // ✅
        this.tarotService = new DailyTarotService();
    }

    public void sendNewsletterToAllUsers() {
        logger.info("📧 Запуск утренней рассылки...");
        Map<String, List<Long>> usersByCity = new HashMap<>();
        List<Long> allUserIds = databaseManager.getAllUserIds();

        for (Long userId : allUserIds) {
            String city = databaseManager.getUserCity(userId);
            if (city != null && !city.trim().isEmpty()) {
                usersByCity.computeIfAbsent(city, k -> new ArrayList<>()).add(userId);
            }
        }

        // 2. ДОБАВЛЯЕМ ПОЛЬЗОВАТЕЛЕЙ БЕЗ ГОРОДА (им отправим нейтральный прогноз)
        List<Long> usersWithoutCity = new ArrayList<>();
        for (Long userId : allUserIds) {
            String city = databaseManager.getUserCity(userId);
            if (city == null || city.trim().isEmpty()) {
                usersWithoutCity.add(userId);
            }
        }

        if (!usersWithoutCity.isEmpty()) {
            usersByCity.put("", usersWithoutCity); // ключ "" = без города
        }

        // 3. ОТПРАВЛЯЕМ РАССЫЛКУ ПО ГРУППАМ
        int sentCount = 0;
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", new Locale("ru")));

        for (Map.Entry<String, List<Long>> entry : usersByCity.entrySet()) {
            String city = entry.getKey();
            List<Long> userIds = entry.getValue();

            // Получаем прогноз (с кэшированием!)

            String weather = city.isEmpty()
                    ? "🌤️ Город не указан — не могу показать погоду."
                    : weatherService.getTodayForecast(city);

            for (Long userId : userIds) {
                try {
                    DailyTarotService.TarotReading reading = tarotService.getRandomReading();
                    String message = String.format("""
                                    ☀️ *Доброе утро!*
                                    
                                    Погода на %s:
                                    %s
                                    
                                    🃏 *Карта дня:* %s
                                    _%s_
                                    
                                    📝 Не забудьте обновить свой to-do список!
                                    Используйте команду /todo, чтобы добавить задачи на сегодня.
                                    """,
                            date,
                            weather,
                            reading.cardName(),
                            reading.fullMeaning()
                    );

                    // В MorningNewsletterService.sendNewsletterToAllUsers()
                    SendMessage msg = SendMessage.builder()
                            .chatId(userId.toString())
                            .text(message)
                            .parseMode("Markdown")
                            .replyMarkup(KeyboardService.mainMenu()) // ← единая клавиатура
                            .build();

                    bot.execute(msg);
                    sentCount++;
                } catch (TelegramApiException e) {
                    logger.warn("Не удалось отправить рассылку пользователю {}: {}", userId, e.getMessage());
                } catch (Exception e) {
                    logger.error("Ошибка при отправке рассылки пользователю: " + userId, e);
                }
            }
        }

        logger.info("✅ Утренняя рассылка отправлена {} пользователям", sentCount);
    }

}
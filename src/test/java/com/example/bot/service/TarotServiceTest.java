package com.example.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TarotServiceTest {

    private TarotService tarotService;

    @BeforeEach
    void setUp() {
        // Подменяем Random на детерминированный
        tarotService = new TarotService(new Random(42));
    }

    @Test
    void getRandomReading_returnsValidReading() {
        TarotService.TarotReading reading = tarotService.getRandomReading();

        assertNotNull(reading);
        assertNotNull(reading.getCardName());
        assertNotNull(reading.getFullMeaning());
        assertFalse(reading.getCardName().isEmpty());
        assertFalse(reading.getFullMeaning().isEmpty());
    }

    @Test
    void getRandomReading_withFallback_returnsFallbackCard() {
        // Создаём сервис с пустым списком карт
        TarotService emptyService = new TarotService(new Random(1)) {
            {
                try {
                    java.lang.reflect.Field cardsField = TarotService.class.getDeclaredField("cards");
                    cardsField.setAccessible(true);
                    cardsField.set(this, java.util.List.of());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        TarotService.TarotReading reading = emptyService.getRandomReading();
        assertEquals("Ошибка", reading.getCardName());
        assertTrue(reading.getFullMeaning().contains("Не удалось загрузить"));
    }

    @Test
    void getRandomReading_positionIsEitherUprightOrReversed() {
        // Проверим, что оба положения возможны
        boolean foundUpright = false;
        boolean foundReversed = false;

        // Используем разные seed'ы для Random
        for (int seed : new int[]{0, 1, 2, 3, 4, 5}) {
            TarotService.TarotReading reading = getTarotReading(seed);
            if (reading.getFullMeaning().contains("Прямое положение")) {
                foundUpright = true;
            }
            if (reading.getFullMeaning().contains("Перевёрнутое положение")) {
                foundReversed = true;
            }

            if (foundUpright && foundReversed) break;
        }

        assertTrue(foundUpright, "Должно быть хотя бы одно прямое положение");
        assertTrue(foundReversed, "Должно быть хотя бы одно перевёрнутое положение");
    }

    private TarotService.TarotReading getTarotReading(int seed) {
        TarotService service = new TarotService(new Random(seed)) {
            {
                try {
                    java.lang.reflect.Field cardsField = TarotService.class.getDeclaredField("cards");
                    cardsField.setAccessible(true);
                    cardsField.set(this, java.util.List.of(
                            new TarotCard("Шут", "Начало", "Хаос")
                    ));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        return service.getRandomReading();
    }
}
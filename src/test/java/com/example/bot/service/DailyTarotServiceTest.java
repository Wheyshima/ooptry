// com.example.bot.service/DailyTarotServiceTest.java
package com.example.bot.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DailyTarotServiceTest {

    @Test
    void getRandomReading_returnsValidCardInUprightPosition() {
        // Given: подменяем Random, чтобы always возвращал прямую позицию
        Random fixedRandom = new Random() {
            @Override
            public boolean nextBoolean() {
                return true; // всегда прямая
            }

            @Override
            public int nextInt(int bound) {
                return 0; // первая карта
            }
        };

        TarotCardStorage mockStorage = Mockito.mock(TarotCardStorage.class);
        Mockito.when(mockStorage.loadCards()).thenReturn(List.of(
                new TarotCard("Маг", "Сила и умение", "Манипуляции")
        ));

        DailyTarotService service = new DailyTarotService(fixedRandom) {
            @Override
            protected TarotCardStorage createTarotCardStorage() {
                return mockStorage;
            }
        };

        // When
        DailyTarotService.TarotReading reading = service.getRandomReading();

        // Then
        assertEquals("Маг", reading.cardName());
        assertEquals("Прямое положение: Сила и умение", reading.fullMeaning());
    }

    @Test
    void getRandomReading_returnsValidCardInReversedPosition() {
        // Given: Random возвращает false (перевёрнутая)
        Random fixedRandom = new Random() {
            @Override
            public boolean nextBoolean() { return false; }
            @Override
            public int nextInt(int bound) { return 0; }
        };

        TarotCardStorage mockStorage = Mockito.mock(TarotCardStorage.class);
        Mockito.when(mockStorage.loadCards()).thenReturn(List.of(
                new TarotCard("Жрица", "Интуиция", "Секретность")
        ));

        DailyTarotService service = new DailyTarotService(fixedRandom) {
            @Override
            protected TarotCardStorage createTarotCardStorage() {
                return mockStorage;
            }
        };

        // When & Then
        DailyTarotService.TarotReading reading = service.getRandomReading();
        assertEquals("Жрица", reading.cardName());
        assertEquals("Перевёрнутое положение: Секретность", reading.fullMeaning());
    }

    @Test
    void getRandomReading_handlesEmptyCardsList() {
        // Given: пустой список карт
        TarotCardStorage mockStorage = Mockito.mock(TarotCardStorage.class);
        Mockito.when(mockStorage.loadCards()).thenReturn(List.of());

        DailyTarotService service = new DailyTarotService(new Random()) {
            @Override
            protected TarotCardStorage createTarotCardStorage() {
                return mockStorage;
            }
        };

        // When & Then
        DailyTarotService.TarotReading reading = service.getRandomReading();
        assertEquals("Ошибка", reading.cardName());
        assertEquals("Нет доступных карт Таро", reading.fullMeaning());
    }
}
package com.example.bot.service;

import com.example.bot.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MorningNewsletterServiceTest {

    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private AbsSender mockBot;

    private MorningNewsletterService newsletterService;
    private WeatherService mockWeatherService;
    private DailyTarotService mockTarotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Мокаем зависимости
        mockWeatherService = mock(WeatherService.class);
        mockTarotService = mock(DailyTarotService.class);

        // Создаём сервис через рефлексию или сеттеры
        newsletterService = new MorningNewsletterService(mockDatabaseManager, mockBot, "fake-api-key") {
            {
                try {
                    // Подменяем зависимости
                    java.lang.reflect.Field weatherField = MorningNewsletterService.class.getDeclaredField("weatherService");
                    weatherField.setAccessible(true);
                    weatherField.set(this, mockWeatherService);

                    java.lang.reflect.Field tarotField = MorningNewsletterService.class.getDeclaredField("tarotService");
                    tarotField.setAccessible(true);
                    tarotField.set(this, mockTarotService);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Test
    void sendNewsletterToAllUsers_sendsMessagesToUsersWithAndWithoutCity() throws TelegramApiException {
        // GIVEN
        List<Long> userIds = Arrays.asList(1L, 2L, 3L, 4L);
        when(mockDatabaseManager.getAllUserIds()).thenReturn(userIds);

        // Города пользователей
        when(mockDatabaseManager.getUserCity(1L)).thenReturn("Москва");
        when(mockDatabaseManager.getUserCity(2L)).thenReturn("Санкт-Петербург");
        when(mockDatabaseManager.getUserCity(3L)).thenReturn(""); // без города
        when(mockDatabaseManager.getUserCity(4L)).thenReturn(null); // без города

        // Прогнозы
        when(mockWeatherService.getTodayForecast("Москва"))
                .thenReturn("☀️ Ясно, от +5°C до +10°C");
        when(mockWeatherService.getTodayForecast("Санкт-Петербург"))
                .thenReturn("⛅ Облачно, от +3°C до +7°C");

        // Карта дня
        DailyTarotService.TarotReading mockReading = new DailyTarotService.TarotReading("Сила", "Победа над трудностями");
        when(mockTarotService.getRandomReading()).thenReturn(mockReading);

        // WHEN
        newsletterService.sendNewsletterToAllUsers();

        // THEN: проверяем отправку 4 сообщений
        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(mockBot, times(4)).execute(messageCaptor.capture());

        List<SendMessage> messages = messageCaptor.getAllValues();

        // Проверка структуры сообщений
        for (SendMessage msg : messages) {
            assertNotNull(msg.getText());
            assertTrue(msg.getText().contains("Доброе утро"));
            assertTrue(msg.getText().contains("*Карта дня:*"));
            assertNotNull(msg.getReplyMarkup());
        }

        // Проверка прогноза для пользователя без города
        SendMessage msgForUser3 = messages.stream()
                .filter(m -> m.getChatId().equals("3"))
                .findFirst()
                .orElseThrow();
        assertTrue(msgForUser3.getText().contains("Город не указан"));

        // Проверка прогноза для Москвы
        SendMessage msgForUser1 = messages.stream()
                .filter(m -> m.getChatId().equals("1"))
                .findFirst()
                .orElseThrow();
        assertTrue(msgForUser1.getText().contains("Ясно"));
    }

    @Test
    void sendNewsletterToAllUsers_handlesTelegramApiException_gracefully() throws TelegramApiException {
        // GIVEN
        when(mockDatabaseManager.getAllUserIds()).thenReturn(List.of(1L, 2L));
        when(mockDatabaseManager.getUserCity(1L)).thenReturn("Москва");
        when(mockDatabaseManager.getUserCity(2L)).thenReturn("Москва");

        when(mockWeatherService.getTodayForecast("Москва"))
                .thenReturn("☀️ Ясно");
        when(mockTarotService.getRandomReading())
                .thenReturn(new DailyTarotService.TarotReading("Звезда", "Надежда"));

        // Имитируем ошибку при отправке второго сообщения
        doThrow(new TelegramApiException("Forbidden"))
                .when(mockBot).execute(any(SendMessage.class));

        // WHEN & THEN — не должно быть исключения
        assertDoesNotThrow(() -> newsletterService.sendNewsletterToAllUsers());

        // Проверяем, что execute вызывался 2 раза
        verify(mockBot, times(2)).execute(any(SendMessage.class));
    }

    @Test
    void sendNewsletterToAllUsers_emptyUserList_sendsNothing() throws TelegramApiException {
        // GIVEN
        when(mockDatabaseManager.getAllUserIds()).thenReturn(List.of());

        // WHEN
        newsletterService.sendNewsletterToAllUsers();

        // THEN
        verify(mockBot, never()).execute(any(SendMessage.class));
    }

    @Test
    void sendNewsletterToAllUsers_allUsersWithoutCity_sendsNeutralForecast() throws TelegramApiException {
        // GIVEN
        when(mockDatabaseManager.getAllUserIds()).thenReturn(List.of(1L, 2L));
        when(mockDatabaseManager.getUserCity(1L)).thenReturn("");
        when(mockDatabaseManager.getUserCity(2L)).thenReturn(null);

        when(mockTarotService.getRandomReading())
                .thenReturn(new DailyTarotService.TarotReading("Отшельник", "Размышление"));

        // WHEN
        newsletterService.sendNewsletterToAllUsers();

        // THEN
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(mockBot, times(2)).execute(captor.capture());

        for (SendMessage msg : captor.getAllValues()) {
            assertTrue(msg.getText().contains("Город не указан — не могу показать погоду."));
        }
    }
}
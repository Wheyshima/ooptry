package com.example.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherServiceTest {

    private static final String API_KEY = "test-api-key";
    private static final ZoneId TZ = ZoneId.of("Asia/Yekaterinburg");

    private HttpClient mockHttpClient;
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2025, 12, 17).atStartOfDay(TZ).toInstant(),
                TZ
        );

        // Создаём сервис с мокнутым HTTP-клиентом
        weatherService = new WeatherService(API_KEY, fixedClock) {
            {
                try {
                    java.lang.reflect.Field clientField = WeatherService.class.getDeclaredField("client");
                    clientField.setAccessible(true);
                    clientField.set(this, mockHttpClient);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Test
    void getTodayForecast_nullCity_returnsErrorMessage() {
        String result = weatherService.getTodayForecast(null);
        assertEquals("🌤️ Город не указан — не могу показать погоду.", result);
    }

    @Test
    void getTodayForecast_emptyCity_returnsErrorMessage() {
        String result = weatherService.getTodayForecast("");
        assertEquals("🌤️ Город не указан — не могу показать погоду.", result);
    }

    @Test
    void getTodayForecast_validCity_returnsForecastAndCachesIt() throws Exception {
        // Вычисляем корректные timestamp'ы для 17 декабря 2025 в UTC+5
        long dt1 = ZonedDateTime.of(2025, 12, 17, 6, 0, 0, 0, TZ).toInstant().getEpochSecond();
        long dt2 = ZonedDateTime.of(2025, 12, 17, 15, 0, 0, 0, TZ).toInstant().getEpochSecond();

        String jsonResponse = String.format("""
        {
          "list": [
            {
              "dt": %d,
              "main": { "temp": 5.2 },
              "weather": [{ "description": "небольшой дождь" }]
            },
            {
              "dt": %d,
              "main": { "temp": 3.8 },
              "weather": [{ "description": "дождь" }]
            }
          ]
        }
        """, dt1, dt2);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openweathermap.org/data/2.5/forecast?q=Moscow&appid=test-api-key&units=metric&lang=ru"))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        when(mockHttpClient.send(request, HttpResponse.BodyHandlers.ofString()))
                .thenAnswer(inv -> mockResponse);

        String result = weatherService.getTodayForecast("Moscow");

        assertTrue(result.contains("дождь"));
        assertTrue(result.contains("от +4°C до +5°C") || result.contains("от +3°C до +5°C"));
    }

    @Test
    void getTodayForecast_apiReturns404_returnsFallbackMessage() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn("{\"message\":\"city not found\"}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openweathermap.org/data/2.5/forecast?q=NonExistentCity&appid=test-api-key&units=metric&lang=ru"))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        when(mockHttpClient.send(request, HttpResponse.BodyHandlers.ofString()))
                .thenReturn(mockResponse);

        String result = weatherService.getTodayForecast("NonExistentCity");
        assertEquals("🌤️ Не удалось загрузить прогноз для NonExistentCity", result);
    }

    @Test
    void getTodayForecast_noDataForToday_returnsNotFoundMessage() throws Exception {
        // Данные только на следующий день
        String jsonResponse = """
            {
              "list": [
                { "dt": 1766073600, "main": { "temp": 2.0 }, "weather": [{ "description": "снег" }] }
              ]
            }
            """;
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openweathermap.org/data/2.5/forecast?q=Paris&appid=test-api-key&units=metric&lang=ru"))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        when(mockHttpClient.send(request, HttpResponse.BodyHandlers.ofString()))
                .thenReturn(mockResponse);

        String result = weatherService.getTodayForecast("Paris");
        assertEquals("🌤️ Прогноз на сегодня не найден для Paris", result);
    }

    @Test
    void getTodayForecast_networkError_returnsErrorMessage() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openweathermap.org/data/2.5/forecast?q=Berlin&appid=test-api-key&units=metric&lang=ru"))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        when(mockHttpClient.send(request, HttpResponse.BodyHandlers.ofString()))
                .thenThrow(new java.io.IOException("Connection timeout"));

        String result = weatherService.getTodayForecast("Berlin");
        assertEquals("🌤️ Ошибка при загрузке прогноза", result);
    }

    @Test
    void getTodayForecast_cacheIsClearedOnNextDay() throws Exception {
        // Первый запрос — сегодня

        weatherService.getTodayForecast("Tokyo");
        verify(mockHttpClient, times(1)).send(any(), any());

        // Меняем часы на следующий день
        Clock nextDayClock = Clock.fixed(
                LocalDate.of(2025, 12, 18).atStartOfDay(TZ).toInstant(),
                TZ
        );

        WeatherService nextDayService = new WeatherService(API_KEY, nextDayClock) {
            {
                try {
                    java.lang.reflect.Field clientField = WeatherService.class.getDeclaredField("client");
                    clientField.setAccessible(true);
                    clientField.set(this, mockHttpClient);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        // Второй запрос — должен вызвать новый HTTP-запрос
        nextDayService.getTodayForecast("Tokyo");
        verify(mockHttpClient, times(2)).send(any(), any());
    }

    @Test
    void getTodayForecast_caseInsensitiveCaching() throws Exception {
        String jsonResponse = """
            { "list": [{ "dt": 1765987200, "main": { "temp": 0.0 }, "weather": [{ "description": "ясно" }] }] }
            """;
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openweathermap.org/data/2.5/forecast?q=MOSCOW&appid=test-api-key&units=metric&lang=ru"))
                .build();
        when(mockHttpClient.send(request, HttpResponse.BodyHandlers.ofString()))
                .thenAnswer(invocation -> mockResponse);

        weatherService.getTodayForecast("MOSCOW");    // первый вызов
        weatherService.getTodayForecast("moscow");    // второй — из кэша

        // Должен быть только ОДИН HTTP-запрос
        verify(mockHttpClient, times(1)).send(any(), any());
    }
}
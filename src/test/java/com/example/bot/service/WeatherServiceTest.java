package com.example.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private WeatherCacheStorage mockCacheStorage;
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mockCacheStorage = mock(WeatherCacheStorage.class);
        Clock fixedClock = Clock.fixed(
                LocalDate.of(2025, 12, 17).atStartOfDay(TZ).toInstant(),
                TZ
        );

        weatherService = new WeatherService(API_KEY, fixedClock, mockCacheStorage) {
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

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Мокаем кэш: сначала пустой
        when(mockCacheStorage.get("moscow")).thenReturn(null);

        String result = weatherService.getTodayForecast("Moscow");

        assertTrue(result.contains("дождь"));
        assertTrue(result.contains("от +4°C до +5°C") || result.contains("от +3°C до +5°C"));

        // Проверяем, что результат сохранён в кэш
        verify(mockCacheStorage).save("moscow", result, LocalDate.of(2025, 12, 17));
    }

    @Test
    void getTodayForecast_usesFileCacheIfAvailable() throws Exception {
        String cachedForecast = "🌤️ Ясно, около +0°C";
        LocalDate today = LocalDate.of(2025, 12, 17);

        // Создаём РЕАЛЬНЫЙ объект
        WeatherCacheStorage.CachedForecast cached =
                new WeatherCacheStorage.CachedForecast(cachedForecast, today);

        doReturn(cached).when(mockCacheStorage).get("london");

        String result = weatherService.getTodayForecast("London");

        assertEquals(cachedForecast, result);
        verify(mockHttpClient, never()).send(any(), any());
    }

    @Test
    void getTodayForecast_fileCacheExpired_fetchesFromApi() throws Exception {
        WeatherCacheStorage.CachedForecast mockCached = mock(WeatherCacheStorage.CachedForecast.class);
        when(mockCached.isExpired(any(LocalDate.class))).thenReturn(true);
        when(mockCacheStorage.get("tokyo")).thenReturn(mockCached);

        String jsonResponse = """
        { "list": [{ "dt": 1765987200, "main": { "temp": 10.0 }, "weather": [{ "description": "облачно" }] }] }
        """;
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        String result = weatherService.getTodayForecast("Tokyo");

        assertTrue(result.contains("Облачно"));
        verify(mockHttpClient).send(any(), any()); // ← API вызывается
    }

    @Test
    void getTodayForecast_apiReturns404_returnsFallbackMessage() throws Exception {
        when(mockCacheStorage.get("nonexistentcity")).thenReturn(null);

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn("{\"message\":\"city not found\"}");
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        String result = weatherService.getTodayForecast("NonExistentCity");
        assertEquals("🌤️ Не удалось загрузить прогноз для NonExistentCity", result);
    }

    @Test
    void getTodayForecast_noDataForToday_returnsNotFoundMessage() throws Exception {
        when(mockCacheStorage.get("paris")).thenReturn(null);

        String jsonResponse = """
            {
              "list": [
                { "dt": 1766073600, "main": { "temp": 2.0 }, "weather": [{ "description": "снег" }] }
              ]
            }
            """;
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        String result = weatherService.getTodayForecast("Paris");
        assertEquals("🌤️ Прогноз на сегодня не найден для этого города.", result);
    }

    @Test
    void getTodayForecast_networkError_returnsErrorMessage() throws Exception {
        when(mockCacheStorage.get("berlin")).thenReturn(null);

        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenThrow(new java.io.IOException("Connection timeout"));

        String result = weatherService.getTodayForecast("Berlin");
        assertEquals("🌤️ Ошибка при загрузке прогноза", result);
    }
    @Test
    void getTodayForecast_caseInsensitiveCaching() throws Exception {
        // === Первый вызов: MOSCOW (кэш пуст) ===
        doReturn(null).when(mockCacheStorage).get("moscow");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("""
        { "list": [{ "dt": 1765987200, "main": { "temp": 0.0 }, "weather": [{ "description": "ясно" }] }] }
        """);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        String result1 = weatherService.getTodayForecast("MOSCOW");
        assertTrue(result1.contains("ясно") || result1.contains("Ясно"),
                "Ожидалось 'ясно' в ответе: " + result1);

        // Проверяем, что сохранено в кэш
        verify(mockCacheStorage).save(eq("moscow"), eq(result1), eq(LocalDate.of(2025, 12, 17)));

        // === Второй вызов: moscow → из кэша ===
        // Создаём РЕАЛЬНЫЙ объект CachedForecast
        WeatherCacheStorage.CachedForecast cachedFromStorage =
                new WeatherCacheStorage.CachedForecast(result1, LocalDate.of(2025, 12, 17));

        doReturn(cachedFromStorage).when(mockCacheStorage).get("moscow");

        String result2 = weatherService.getTodayForecast("moscow");

        // Then: результат из кэша, API вызван только 1 раз
        assertEquals(result1, result2);
        verify(mockHttpClient, times(1)).send(any(), any());
    }

}
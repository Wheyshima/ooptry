package com.example.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";
    private static final ZoneId TZ = ZoneId.of("Asia/Yekaterinburg");

    private final String apiKey;
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, CachedForecast> cache;
    private final WeatherCacheStorage cacheStorage;
    private final Clock clock;

    // === Конструкторы ===

    public WeatherService(String openWeatherApiKey) {
        this(openWeatherApiKey, Clock.systemDefaultZone());
    }
    // Пакетно-видимый конструктор для продакшена с кастомными часами
    WeatherService(String openWeatherApiKey, Clock clock) {
        this(openWeatherApiKey, clock, new WeatherCacheStorage()); // ← делегирование
    }
    // Пакетно-видимый конструктор для тестов
    WeatherService(String openWeatherApiKey, Clock clock, WeatherCacheStorage cacheStorage) {
        if (openWeatherApiKey == null || openWeatherApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenWeather API key is required");
        }
        this.apiKey = openWeatherApiKey.trim();
        this.clock = clock;
        this.client = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.cache = new ConcurrentHashMap<>();
        this.cacheStorage = cacheStorage; // ← инжектируем мок
    }

    // === Вложенные классы ===

    private static class CachedForecast {
        final String text;
        final LocalDate cachedAt;

        CachedForecast(String text, LocalDate cachedAt) {
            this.text = text;
            this.cachedAt = cachedAt;
        }

        boolean isExpired(LocalDate today) {
            return !cachedAt.equals(today);
        }
    }

    // === Public API ===

    /**
     * Возвращает прогноз погоды на сегодня с кэшированием (1 запрос на город в день).
     */
    public String getTodayForecast(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            return "🌤️ Город не указан — не могу показать погоду.";
        }

        String normalizedCity = cityName.trim();
        String cacheKey = normalizedCity.toLowerCase(Locale.ROOT);
        LocalDate today = now();

        // Удаление устаревших записей (защита от утечки памяти)
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(today));
        cacheStorage.removeExpired(today);
        // Сначала проверяем быстрый RAM-кэш
        CachedForecast cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired(today)) {
            return cached.text;
        }
        //  Если нет в RAM — проверяем файловый кэш
        WeatherCacheStorage.CachedForecast fileCached = cacheStorage.get(cacheKey);
        if (fileCached != null && !fileCached.isExpired(today)) {
            // Загружаем в RAM для ускорения последующих запросов
            CachedForecast ramCached = new CachedForecast(fileCached.text, fileCached.cachedAt);
            cache.put(cacheKey, ramCached);
            return fileCached.getText(); // ← правильно
        }

        String forecast = fetchForecastFromApi(normalizedCity);
        CachedForecast newCached = new CachedForecast(forecast, today);

        cache.put(cacheKey, newCached);
        cacheStorage.save(cacheKey, forecast, today);
        return forecast;
    }

    // === Private helpers ===

    private LocalDate now() {
        return LocalDate.now(clock.withZone(TZ));
    }

    private String fetchForecastFromApi(String cityName) {
        System.out.println("🌍 Запрос погоды из API для города: {}"+ cityName);

        try {
            String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
            String url = String.format(
                    "%s?q=%s&appid=%s&units=metric&lang=ru",
                    FORECAST_URL, encodedCity, apiKey
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Forecast API error {}: {}", response.statusCode(), response.body());
                return "🌤️ Не удалось загрузить прогноз для " + cityName;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode list = root.path("list");
            if (list.isMissingNode() || list.isEmpty()) {
                return "🌤️ Прогноз для " + cityName + " пуст";
            }

            return processForecastData(list, now());

        } catch (Exception e) {
            logger.error("Ошибка при получении прогноза для города: {}", cityName, e);
            return "🌤️ Ошибка при загрузке прогноза";
        }
    }

    private String processForecastData(JsonNode list, LocalDate today) {
        double minTemp = Double.POSITIVE_INFINITY;
        double maxTemp = Double.NEGATIVE_INFINITY;
        String description = null;

        for (Iterator<JsonNode> it = list.elements(); it.hasNext(); ) {
            JsonNode item = it.next();
            long dt = item.path("dt").asLong(0);
            if (dt == 0) continue;

            LocalDateTime itemTime = Instant.ofEpochSecond(dt).atZone(TZ).toLocalDateTime();
            if (!itemTime.toLocalDate().equals(today)) {
                continue;
            }

            JsonNode main = item.path("main");
            if (!main.isMissingNode()) {
                double temp = main.path("temp").asDouble(Double.NaN);
                if (!Double.isNaN(temp)) {
                    minTemp = Math.min(minTemp, temp);
                    maxTemp = Math.max(maxTemp, temp);
                }
            }

            if (description == null && item.has("weather")) {
                JsonNode weatherArray = item.get("weather");
                if (weatherArray.isArray() && !weatherArray.isEmpty()) {
                    JsonNode weather = weatherArray.get(0);
                    if (weather.has("description")) {
                        description = weather.get("description").asText();
                    }
                }
            }
        }

        if (Double.isInfinite(minTemp)) {
            return "🌤️ Прогноз на сегодня не найден для этого города.";
        }

        int min = (int) Math.round(minTemp);
        int max = (int) Math.round(maxTemp);

        String minStr = formatTemperature(min);
        String maxStr = formatTemperature(max);
        String desc = description != null ? capitalize(description) : "погода";
        String emoji = getWeatherEmoji(desc);

        if (min == max) {
            return String.format("%s %s, около %s°C", emoji, desc, minStr);
        } else {
            return String.format("%s %s, от %s°C до %s°C", emoji, desc, minStr, maxStr);
        }
    }

    private String formatTemperature(int temp) {
        return temp >= 0 ? "+" + temp : Integer.toString(temp);
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase(Locale.ROOT) +
                input.substring(1).toLowerCase(Locale.ROOT);
    }

    private String getWeatherEmoji(String description) {
        if (description == null) {
            return "🌤️";
        }
        String lower = description.toLowerCase(Locale.ROOT);
        if (lower.contains("ясно") || lower.contains("солнечно")) {
            return "☀️";
        }
        if (lower.contains("облачно") || lower.contains("переменная")) {
            return "⛅";
        }
        if (lower.contains("дождь") || lower.contains("ливень")) {
            return "🌧️";
        }
        if (lower.contains("снег")) {
            return "❄️";
        }
        if (lower.contains("туман")) {
            return "🌫️";
        }
        return "🌤️";
    }
}
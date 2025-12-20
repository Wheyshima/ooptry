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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WeatherService {
    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast"; // ← пробелы удалены
    private final String apiKey;
    private static final ZoneId TZ = ZoneId.of("Asia/Yekaterinburg");

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, CachedForecast> cache;
    private final Clock clock;

    // Основной конструктор (для продакшена)
    public WeatherService(String openWeatherApiKey) {
        this(openWeatherApiKey, Clock.systemDefaultZone());
    }

    // Пакетно-видимый конструктор для тестов
    WeatherService(String openWeatherApiKey, Clock clock) {
        if (openWeatherApiKey == null || openWeatherApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenWeather API key is required");
        }
        this.apiKey = openWeatherApiKey;
        this.clock = clock;
        this.client = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.cache = new ConcurrentHashMap<>();
    }

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

    private LocalDate now() {
        return LocalDate.now(clock.withZone(TZ));
    }

    /**
     * Возвращает прогноз на сегодня с кэшированием (1 запрос на город в день)
     */
    public String getTodayForecast(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            return "🌤️ Город не указан — не могу показать погоду.";
        }
        String key = cityName.trim().toLowerCase(Locale.ROOT); // нормализация ключа
        LocalDate today = now();

        // Опционально: удаляем устаревшие записи (чтобы не росла утечка памяти)
        // Это безопасно для ConcurrentHashMap, но может быть дорого при частых вызовах
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(today));

        CachedForecast cached = cache.get(key);
        if (cached != null && !cached.isExpired(today)) {
            return cached.text;
        }

        String forecast = fetchForecastFromApi(cityName); // передаём оригинальное имя
        cache.put(key, new CachedForecast(forecast, today));
        return forecast;
    }

    private String fetchForecastFromApi(String cityName) {
        try {
            String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
            String url = String.format(
                    "%s?q=%s&appid=%s&units=metric&lang=ru",
                    FORECAST_URL, encodedCity, apiKey
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Forecast API error {}: {}", response.statusCode(), response.body());
                return "🌤️ Не удалось загрузить прогноз для " + cityName;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode list = root.get("list");
            if (list == null || list.isEmpty()) {
                return "🌤️ Прогноз для " + cityName + " пуст";
            }

            LocalDate today = now();
            double minTemp = Double.MAX_VALUE;
            double maxTemp = Double.MIN_VALUE;
            String description = null;

            for (Iterator<JsonNode> it = list.elements(); it.hasNext(); ) {
                JsonNode item = it.next();
                long dt = item.get("dt").asLong();
                LocalDate itemDate = Instant.ofEpochSecond(dt).atZone(TZ).toLocalDate();

                if (itemDate.equals(today)) {
                    JsonNode main = item.get("main");
                    if (main != null) {
                        double temp = main.get("temp").asDouble();
                        minTemp = Math.min(minTemp, temp);
                        maxTemp = Math.max(maxTemp, temp);
                    }
                    if (description == null && item.has("weather")) {
                        JsonNode weather = item.get("weather").get(0);
                        if (weather != null && weather.has("description")) {
                            description = weather.get("description").asText();
                        }
                    }
                }
            }

            if (minTemp == Double.MAX_VALUE) {
                return "🌤️ Прогноз на сегодня не найден для " + cityName;
            }

            int min = (int) Math.round(minTemp);
            int max = (int) Math.round(maxTemp);
            String desc = description != null ? capitalize(description) : "погода";
            String emoji = getWeatherEmoji(desc);

            if (min == max) {
                return String.format("%s %s, около %s%d°C", emoji, desc, min >= 0 ? "+" : "", min);
            } else {
                return String.format("%s %s, от %s%d°C до %s%d°C", emoji, desc, min >= 0 ? "+" : "", min, max >= 0 ? "+" : "", max);
            }

        } catch (Exception e) {
            logger.error("Ошибка прогноза для: {}", cityName, e);
            return "🌤️ Ошибка при загрузке прогноза";
        }
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
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
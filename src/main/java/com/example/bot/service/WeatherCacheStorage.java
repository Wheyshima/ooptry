package com.example.bot.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class WeatherCacheStorage {
    private static final Logger logger = LoggerFactory.getLogger(WeatherCacheStorage.class);
    private static final String CACHE_DIR = "data";
    private static final String CACHE_FILE = "weather_cache.json";
    private final Path cachePath;
    private final ObjectMapper objectMapper;
    private Map<String, CachedForecast> cacheMap = new HashMap<>();
    // Основной конструктор (для продакшена)
    public WeatherCacheStorage() {
        this(Paths.get(CACHE_DIR, CACHE_FILE));
    }
    // Пакетно-видимый конструктор для тестов
    WeatherCacheStorage(Path cachePath) {
        this.cachePath = cachePath;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadCacheFromFile();
    }
    private Path getCachePath() {
        return cachePath;
    }
    public void save(String cityName, String forecast, LocalDate date) {
        cacheMap.put(cityName.toLowerCase(), new CachedForecast(forecast, date));
        saveCacheToFile();
    }

    public CachedForecast get(String cityName) {
        return cacheMap.get(cityName.toLowerCase());
    }

    public void removeExpired(LocalDate today) {
        cacheMap.entrySet().removeIf(entry -> entry.getValue().isExpired(today));
        saveCacheToFile();
    }

    private void saveCacheToFile() {
        try {
            Files.createDirectories(cachePath.getParent());

            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(cacheMap);

            Files.writeString(cachePath, json, StandardCharsets.UTF_8);

            System.out.println("✅ Кэш погоды сохранён в " + cachePath.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("❌ Ошибка при сохранении кэша погоды: " + e.getMessage());
        }
    }

    private void loadCacheFromFile() {
        try {
            Files.createDirectories(cachePath.getParent());
            if (Files.exists(cachePath)) {
                String json = Files.readString(cachePath, StandardCharsets.UTF_8);
                cacheMap = objectMapper.readValue(json, new TypeReference<>() {
                });
                System.out.println("✅ Кэш погоды загружен из файла");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось загрузить кэш погоды из файла: " + e.getMessage());
        }
    }

    public static class CachedForecast { // ← static!
        public final String text;
        public final LocalDate cachedAt;

        @JsonCreator
        public CachedForecast(
                @JsonProperty("text") String text,
                @JsonProperty("cachedAt") LocalDate cachedAt
        ) {
            this.text = text;
            this.cachedAt = cachedAt;
        }

        public String getText() { return text; }
        public LocalDate getCachedAt() { return cachedAt; }

        public boolean isExpired(LocalDate today) {
            return !cachedAt.equals(today);
        }
    }
}
package com.example.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WeatherCacheStorageTest {

    @TempDir
    Path tempDir;

    private Path cacheFile;
    private WeatherCacheStorage cacheStorage;

    @BeforeEach
    void setUp() {
        cacheFile = tempDir.resolve("weather_cache.json");
        cacheStorage = new WeatherCacheStorage(cacheFile);
    }

    @Test
    void saveAndLoad_CacheRoundTrip() {
        String city = "Екатеринбург";
        String forecast = "❄️ Снег";
        LocalDate date = LocalDate.of(2025, 12, 19);

        cacheStorage.save(city, forecast, date);
        var loaded = cacheStorage.get(city);

        assertNotNull(loaded);
        assertEquals(forecast, loaded.getText());
        assertEquals(date, loaded.getCachedAt());
    }

    @Test
    void get_NonExistentCity_ReturnsNull() {
        WeatherCacheStorage.CachedForecast result = cacheStorage.get("НеизвестныйГород");
        assertNull(result);
    }

    @Test
    void caseInsensitiveKeys() {
        cacheStorage.save("MOSCOW", "🌤️", LocalDate.of(2025, 12, 19));
        WeatherCacheStorage.CachedForecast result1 = cacheStorage.get("moscow");
        WeatherCacheStorage.CachedForecast result2 = cacheStorage.get("Moscow");

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("🌤️", result1.getText());
        assertEquals("🌤️", result2.getText());
    }

    @Test
    void removeExpired_RemovesOnlyOldEntries() {
        LocalDate today = LocalDate.of(2025, 12, 19);

        cacheStorage.save("OldCity", "🌧️", LocalDate.of(2025, 12, 18));
        cacheStorage.save("TodayCity", "☀️", today);

        cacheStorage.removeExpired(today);

        assertNull(cacheStorage.get("OldCity"));        // удалён
        assertNotNull(cacheStorage.get("TodayCity"));   // остался
    }

    @Test
    void loadFromExistingValidFile() throws IOException {
        // Given: создаём файл вручную в ТОМ ЖЕ пути
        String json = """
        {
          "екатеринбург": {
            "text": "❄️ Снег",
            "cachedAt": "2025-12-19"
          }
        }
        """;
        Files.writeString(cacheFile, json, java.nio.charset.StandardCharsets.UTF_8);

        // When: создаём НОВЫЙ кэш, указывая ТОТ ЖЕ файл
        WeatherCacheStorage newCache = new WeatherCacheStorage(cacheFile);

        // Then
        WeatherCacheStorage.CachedForecast loaded = newCache.get("Екатеринбург");
        assertNotNull(loaded);
        assertEquals("❄️ Снег", loaded.getText());
        assertEquals(LocalDate.of(2025, 12, 19), loaded.getCachedAt());
    }

    @Test
    void handlesEmptyFileGracefully() throws IOException {
        Files.write(cacheFile, new byte[0]); // пустой файл
        WeatherCacheStorage newCache = new WeatherCacheStorage(cacheFile);
        assertNull(newCache.get("Moscow"));
    }

    @Test
    void handlesInvalidJsonGracefully() throws IOException {
        Files.writeString(cacheFile, "{ invalid json }", java.nio.charset.StandardCharsets.UTF_8);
        WeatherCacheStorage newCache = new WeatherCacheStorage(cacheFile);
        assertNull(newCache.get("Moscow"));
    }

    @Test
    void fileIsCreatedOnFirstSave() throws IOException {
        assertFalse(Files.exists(cacheFile));

        cacheStorage.save("Test", "🌤️", LocalDate.now());
        assertTrue(Files.exists(cacheFile));
        assertTrue(Files.size(cacheFile) > 0);
    }

    @Test
    void preservesExistingNonExpiredEntriesAfterSave() {
        LocalDate today = LocalDate.of(2025, 12, 19);
        cacheStorage.save("City1", "🌤️", today);
        cacheStorage.save("City2", "🌧️", today);

        cacheStorage.save("City3", "❄️", today);

        assertEquals("🌤️", cacheStorage.get("City1").getText());
        assertEquals("🌧️", cacheStorage.get("City2").getText());
        assertEquals("❄️", cacheStorage.get("City3").getText());
    }
}
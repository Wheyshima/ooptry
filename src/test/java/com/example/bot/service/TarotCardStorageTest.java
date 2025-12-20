// com.example.bot.service/TarotCardStorageTest.java
package com.example.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TarotCardStorageTest {

    @Mock
    private TarotParser mockTarotParser;

    private TarotCardStorage tarotCardStorage;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir; // автоматически создаётся и удаляется JUnit

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Подменяем путь к файлу на временный
        tarotCardStorage = new TarotCardStorage() {
            @Override
            protected Path getTarotFilePath() {
                return tempDir.resolve("tarot_cards.json");
            }
        };
        tarotCardStorage.tarotParser = mockTarotParser; // внедряем мок
        objectMapper = new ObjectMapper();
    }

    // ========= Тест: загрузка из файла =========

    @Test
    void loadCards_loadsFromFileIfExists() throws IOException {
        // Given: создаём файл с данными
        List<TarotCard> expectedCards = List.of(
                new TarotCard("Маг", "Сила, умение, ресурсы", "Манипуляции, талант без цели")
        );
        Path tarotFile = tarotCardStorage.getTarotFilePath();
        Files.createDirectories(tarotFile.getParent());
        objectMapper.writeValue(tarotFile.toFile(), expectedCards);

        // When
        List<TarotCard> actualCards = tarotCardStorage.loadCards();

        // Then
        assertEquals(expectedCards, actualCards);
    }

    // ========= Тест: парсинг при отсутствии файла =========

    @Test
    void loadCards_parsesFromWebIfFileNotFound() {
        // Given: файл не существует
        Path tarotFile = tarotCardStorage.getTarotFilePath();
        assertFalse(Files.exists(tarotFile));

        // Мок парсера возвращает карты
        List<TarotCard> parsedCards = List.of(
                new TarotCard("Жрица", "Интуиция, тайны, духовность", "Секретность, подавление чувств")
        );
        when(mockTarotParser.parseAllCards()).thenReturn(parsedCards);

        // When
        List<TarotCard> actualCards = tarotCardStorage.loadCards();

        // Then
        assertEquals(parsedCards, actualCards);
        assertTrue(Files.exists(tarotFile)); // файл должен быть создан

        // Проверим, что содержимое файла корректно
        try {
            List<TarotCard> savedCards = objectMapper.readValue(tarotFile.toFile(), objectMapper.getTypeFactory().constructCollectionType(List.class, TarotCard.class));
            assertEquals(parsedCards, savedCards);
        } catch (IOException e) {
            fail("Не удалось прочитать сохранённый файл", e);
        }
    }

    // ========= Тест: пустой результат парсинга → резервные данные =========

    @Test
    void loadCards_usesFallbackIfParseReturnsEmpty() {
        // Given: файл не существует
        when(mockTarotParser.parseAllCards()).thenReturn(List.of()); // пустой список

        // When
        List<TarotCard> cards = tarotCardStorage.loadCards();

        // Then
        assertEquals(1, cards.size());
        assertEquals("Шут", cards.getFirst().name());
    }

    // ========= Тест: ошибка при загрузке → резервные данные =========

    @Test
    void loadCards_usesFallbackOnError() throws IOException {
        // Given: повреждённый файл
        Path tarotFile = tarotCardStorage.getTarotFilePath();
        Files.createDirectories(tarotFile.getParent());
        Files.write(tarotFile, "это_не_json".getBytes());

        // When
        List<TarotCard> cards = tarotCardStorage.loadCards();

        // Then
        assertEquals(1, cards.size());
        assertEquals("Шут", cards.getFirst().name());
    }

    // ========= Тест: пустой файл → парсинг =========

    @Test
    void loadCards_parsesIfFileIsEmpty() throws IOException {
        // Given: пустой файл
        Path tarotFile = tarotCardStorage.getTarotFilePath();
        Files.createDirectories(tarotFile.getParent());
        Files.write(tarotFile, new byte[0]); // пустой файл

        List<TarotCard> parsedCards = List.of(new TarotCard("Император", "Авторитет, структура", "Тирания, жесткость"));
        when(mockTarotParser.parseAllCards()).thenReturn(parsedCards);

        // When
        List<TarotCard> cards = tarotCardStorage.loadCards();

        // Then
        assertEquals(parsedCards, cards);
    }
}
// com.example.bot.service/TarotCardStorage.java
package com.example.bot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TarotCardStorage {
    private static final Logger logger = LoggerFactory.getLogger(TarotCardStorage.class);
    private static final String TAROT_DATA_DIR = "data";
    private static final String TAROT_FILE_NAME = "tarot_cards.json";

    private final ObjectMapper objectMapper;
    TarotParser tarotParser; // не final — чтобы можно было заменить в тестах

    public TarotCardStorage() {
        this.objectMapper = new ObjectMapper();
        this.tarotParser = new TarotParser();
    }

    // === Для тестирования: позволяет подменить путь к файлу ===
    protected Path getTarotFilePath() {
        return Paths.get(TAROT_DATA_DIR, TAROT_FILE_NAME);
    }

    /**
     * Загружает карты: сначала из файла, при ошибке — парсит и сохраняет.
     */
    public List<TarotCard> loadCards() {
        Path tarotFilePath = getTarotFilePath();

        try {
            Files.createDirectories(tarotFilePath.getParent());

            // Пытаемся загрузить из файла
            if (Files.exists(tarotFilePath) && Files.size(tarotFilePath) > 0) {
                logger.info("📂 Загрузка карт Таро из файла: {}", tarotFilePath);
                try (InputStream is = Files.newInputStream(tarotFilePath)) {
                    List<TarotCard> cards = objectMapper.readValue(is, new TypeReference<>() {
                    });
                    if (!cards.isEmpty()) {
                        logger.info("✅ Успешно загружено {} карт из файла", cards.size());
                        return cards;
                    }
                }
            }

            // Если файл пуст/отсутствует — парсим
            logger.warn("⚠️ Файл карт отсутствует или пуст. Запуск парсинга с сайта...");
            List<TarotCard> cards = tarotParser.parseAllCards();

            if (!cards.isEmpty()) {
                saveCardsToFile(cards, tarotFilePath);
                logger.info("✅ Карты успешно сохранены в {}", tarotFilePath);
                return cards;
            } else {
                logger.warn("❌ Парсинг не дал результатов. Используем резервные данные.");
                return createFallbackCards();
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка при загрузке карт Таро", e);
            return createFallbackCards();
        }
    }

    private void saveCardsToFile(List<TarotCard> cards, Path filePath) throws IOException {
        try (OutputStream os = Files.newOutputStream(filePath)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(os, cards);
        }
    }

    private List<TarotCard> createFallbackCards() {
        return List.of(
                new TarotCard(
                        "Шут",
                        "Новые начинания, свобода, спонтанность, путешествие души",
                        "Безрассудство, хаос, необдуманные поступки, неуравновешенность"
                )
        );
    }
}
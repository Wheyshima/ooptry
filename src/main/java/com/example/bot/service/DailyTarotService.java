package com.example.bot.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DailyTarotService {
    private static final String TAROT_CARDS_RESOURCE = "/tarot_cards.json";

    private final List<TarotCard> cards;
    private final Random random;

    public DailyTarotService() {
        this(new Random());
    }

    public DailyTarotService(Random random) {
        this.random = random;
        this.cards = loadCardsFromResource();
    }

    private List<TarotCard> loadCardsFromResource() {
        try {
            InputStream is = getClass().getResourceAsStream(TAROT_CARDS_RESOURCE);
            if (is == null) {
                throw new RuntimeException("Файл " + TAROT_CARDS_RESOURCE + " не найден в ресурсах");
            }
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(is, new TypeReference<>() {
            });
        } catch (Exception e) {
            System.out.println("Не удалось загрузить карты Таро из JSON — использую резервный список"+ e.getMessage());
            return createFallbackCards();
        }
    }

    private List<TarotCard> createFallbackCards() {
        return Collections.singletonList(
                new TarotCard("Шут", "Новые начинания", "Хаос и безрассудство")
        );
    }

    public TarotReading getRandomReading() {
        if (cards.isEmpty()) {
            return new TarotReading("Ошибка", "Нет доступных карт Таро");
        }
        TarotCard card = cards.get(random.nextInt(cards.size()));
        boolean isUpright = random.nextBoolean();
        String meaning = isUpright ? card.upright : card.reversed;
        String position = isUpright ? "Прямое положение" : "Перевёрнутое положение";
        return new TarotReading(card.name, position + ": " + meaning);
    }

    public record TarotCard(String name, String upright, String reversed) {
        @JsonCreator
        public TarotCard(
                @JsonProperty("name") String name,
                @JsonProperty("upright") String upright,
                @JsonProperty("reversed") String reversed
        ) {
            this.name = name;
            this.upright = upright;
            this.reversed = reversed;
        }
    }

    public record TarotReading(String cardName, String fullMeaning) {
    }
}

package com.example.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

public class TarotService {
    private static final Logger logger = LoggerFactory.getLogger(TarotService.class);
    private final List<TarotCard> cards; // ← final допустим, если присвоить ОДИН раз
    private final Random random;

    public TarotService() {
        this(new Random()); // используем стандартный Random
    }

    // Конструктор для тестов — позволяет передать фиксированный Random
    public TarotService(Random random) {
        this.random = random;
        TarotParser parser = new TarotParser();
        List<TarotCard> parsedCards = parser.parseAllCards();

        if (parsedCards.isEmpty()) {
            logger.warn("Не удалось загрузить карты Таро — использую резервный список");
            this.cards = createFallbackCards();
            System.out.println("📊 [DEBUG] Загружено карт: " + cards.size());
        } else {
            this.cards = parsedCards;
            System.out.println("📊 [DEBUG] Загружено карт: " + cards.size());
        }
    }

    public TarotReading getRandomReading() {
        if (cards.isEmpty()) {
            return new TarotReading("Ошибка", "Не удалось загрузить карты Таро");
        }

        TarotCard card = cards.get(random.nextInt(cards.size()));
        boolean isUpright = random.nextBoolean();

        String position = isUpright ? "Прямое положение" : "Перевёрнутое положение";
        String meaning = isUpright ? card.getUpright() : card.getReversed();

        return new TarotReading(card.getName(), position + ": " + meaning);
    }

    private List<TarotCard> createFallbackCards() {
        return List.of(
                new TarotCard("Шут", "Новые начинания, вера в лучшее", "Безответственность, хаос")
        );
    }

    public static class TarotReading {
        private final String cardName;
        private final String fullMeaning;

        public TarotReading(String cardName, String fullMeaning) {
            this.cardName = cardName;
            this.fullMeaning = fullMeaning;
        }

        public String getCardName() { return cardName; }
        public String getFullMeaning() { return fullMeaning; }
    }

    public static class TarotCard {
        private final String name;
        private final String upright;
        private final String reversed;

        public TarotCard(String name, String upright, String reversed) {
            this.name = name;
            this.upright = upright;
            this.reversed = reversed;
        }

        public String getName() { return name; }
        public String getUpright() { return upright; }
        public String getReversed() { return reversed; }
    }
}
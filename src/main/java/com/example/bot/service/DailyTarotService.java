package com.example.bot.service;

import java.util.List;
import java.util.Random;

public class DailyTarotService {
    private final List<TarotCard> cards; // ← теперь TarotCard из внешнего файла
    private final Random random;

    public DailyTarotService() {
        this(new Random());
    }

    public DailyTarotService(Random random) {
        this.random = random;
        this.cards = createTarotCardStorage().loadCards();
    }
    protected TarotCardStorage createTarotCardStorage() {
        return new TarotCardStorage();
    }

    public TarotReading getRandomReading() {
        if (cards.isEmpty()) {
            return new TarotReading("Ошибка", "Нет доступных карт Таро");
        }
        TarotCard card = cards.get(random.nextInt(cards.size()));
        boolean isUpright = random.nextBoolean();
        String meaning = isUpright ? card.upright() : card.reversed(); // ✅ Методы доступны
        String position = isUpright ? "Прямое положение" : "Перевёрнутое положение";
        return new TarotReading(card.name(), position + ": " + meaning);
    }

    public record TarotReading(String cardName, String fullMeaning) {}
}
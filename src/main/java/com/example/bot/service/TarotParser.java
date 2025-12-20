package com.example.bot.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TarotParser {
    private static final Logger logger = LoggerFactory.getLogger(TarotParser.class);
    private static final String BASE_URL = "https://astrohelper.ru";
    private static final String INDEX_URL = "https://astrohelper.ru/gadaniya/taro/znachenie/";

    public List<TarotCard> parseAllCards() {
        logger.info("🔄 Начинаю парсинг всех карт Таро с {}", INDEX_URL);

        try {
            Document doc = Jsoup.connect(INDEX_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            List<TarotCard> cards = new ArrayList<>();
            Elements h2s = doc.select("h2");

            logger.info("Найдено {} заголовков <h2>", h2s.size());

            for (Element h2 : h2s) {
                String title = h2.text();
                if (title.startsWith("Значение карт Таро:")) {
                    String suit = title.replace("Значение карт Таро: ", "").trim();
                    logger.info("🎴 Парсинг масти: {}", suit);

                    Element current = h2;
                    int linkCount = 0;
                    while ((current = current.nextElementSibling()) != null && !current.tagName().equals("h2")) {
                        Elements links = current.select("a[href^=../../../gadaniya/taro/znachenie/]");
                        linkCount += links.size();
                        for (Element link : links) {
                            String href = link.attr("href");
                            String cardUrl = BASE_URL + href.replace("../../../", "/");

                            TarotCard card = parseCard(cardUrl);
                            if (card != null) {
                                cards.add(card);
                            }
                        }
                    }
                    System.out.println("✅ [DEBUG] Масть '" + suit + "': найдено " + linkCount + " ссылок");
                }
            }

            System.out.println("✅ [DEBUG] Всего спарсено карт: " + cards.size());
            logger.info("✅ Успешно спарсено {} карт Таро", cards.size());
            return cards;

        } catch (IOException e) {
            System.err.println("❌ [ERROR] Ошибка сети при парсинге: " + e.getMessage());
            logger.error("❌ Ошибка при парсинге страницы Таро:", e);
            return List.of();
        }
    }

    //парсинг сайта с картой
    private TarotCard parseCard(String url) {
        try {
            logger.debug("📥 Загрузка карты: {}", url);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            Element h1 = doc.selectFirst("h1");
            if (h1 == null) {
                logger.warn("⚠️ Не найден <h1> на странице {}", url);
                return null;
            }
            // Убираем "Аркан:" и прочее
            String name = h1.text()
                    .replace("Аркан", "")
                    .replace(":", "")
                    .replace("Значение и описание", "")
                    .trim();

            String upright = "Прямое значение не найдено";
            String reversed = "Перевёрнутое значение не найдено";

            // Ищем таблицу
            Elements rows = doc.select("table.table-striped tbody tr");
            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() == 2) {
                    String label = cols.get(0).text().toLowerCase().trim();
                    String value = cols.get(1).text().trim();

                    if (label.contains("прямое положение")) {
                        upright = value;
                    } else if (label.contains("перевернутое положение") || label.contains("перевёрнутое положение")) {
                        reversed = value;
                    }
                }
            }

            logger.debug("✅ Успешно спарсена карта: {} | Прямое: {} | Перевёрнутое: {}", name, upright, reversed);
            return new TarotCard(name, upright, reversed);

        } catch (Exception e) {
            logger.warn("⚠️ Ошибка парсинга карты {}: {}", url, e.getMessage());
            return null;
        }
    }

    // НОВЫЙ МЕТОД — для тестов
    TarotCard parseCardFromDocument(Document doc) {
        Element h1 = doc.selectFirst("h1");
        if (h1 == null) {
            logger.warn("⚠️ Не найден <h1> на странице {}", "mock-url");
            return null;
        }
        String name = h1.text()
                .replace("Аркана", "")
                .replace(":", "")
                .replace("Значение и описание", "")
                .trim();

        String upright = "Прямое значение не найдено";
        String reversed = "Перевёрнутое значение не найдено";

        Elements rows = doc.select("table.table-striped tbody tr");
        for (Element row : rows) {
            Elements cols = row.select("td");
            if (cols.size() == 2) {
                String label = cols.get(0).text().toLowerCase().trim();
                String value = cols.get(1).text().trim();

                if (label.contains("прямое положение")) {
                    upright = value;
                } else if (label.contains("перевернутое положение") || label.contains("перевёрнутое положение")) {
                    reversed = value;
                }
            }
        }

        return new TarotCard(name, upright, reversed);
    }
}
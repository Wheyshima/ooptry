// TarotParserTest.java
package com.example.bot.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TarotParserTest {

    @Test
    void parseCardFromDocument_parsesCardCorrectly() {
        // Given: мок HTML-страницы
        String html = """
            <html>
            <body>
                <h1>Маг — значение и описание Аркана</h1>
                <table class="table-striped">
                    <tbody>
                        <tr>
                            <td>Прямое положение</td>
                            <td>Сила, умение, ресурсы</td>
                        </tr>
                        <tr>
                            <td>Перевёрнутое положение</td>
                            <td>Манипуляции, талант без цели</td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
            """;

        Document doc = Jsoup.parse(html);
        TarotParser parser = new TarotParser();

        // When
        TarotCard card = parser.parseCardFromDocument(doc);

        // Then
        assertNotNull(card);
        assertEquals("Маг — значение и описание", card.name());
        assertEquals("Сила, умение, ресурсы", card.upright());
        assertEquals("Манипуляции, талант без цели", card.reversed());
    }

    @Test
    void parseCardFromDocument_handlesMissingH1() {
        String html = "<html><body><p>Нет заголовка</p></body></html>";
        Document doc = Jsoup.parse(html);
        TarotParser parser = new TarotParser();

        TarotCard card = parser.parseCardFromDocument(doc);

        assertNull(card);
    }

    @Test
    void parseCardFromDocument_handlesMissingTable() {
        String html = "<html><body><h1>Шут</h1></body></html>";
        Document doc = Jsoup.parse(html);
        TarotParser parser = new TarotParser();

        TarotCard card = parser.parseCardFromDocument(doc);

        assertNotNull(card);
        assertEquals("Шут", card.name());
        assertEquals("Прямое значение не найдено", card.upright());
        assertEquals("Перевёрнутое значение не найдено", card.reversed());
    }
}
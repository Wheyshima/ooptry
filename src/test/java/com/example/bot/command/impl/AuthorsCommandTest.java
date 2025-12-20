package com.example.bot.command.impl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AuthorsCommandTest {

    @Test
    void shouldReturnAuthorsInfo() {
        AuthorsCommand command = new AuthorsCommand();
        String response = command.execute(null);

        assertTrue(response.contains("Авторы BestDay"));
        assertTrue(response.contains("harumiRui"));
        assertTrue(response.contains("angrycoke"));
        assertTrue(response.contains("дуэт"));
        assertTrue(response.contains("осознанной жизни"));
        assertTrue(response.contains("Наша история"));
        assertTrue(response.contains("Контакты"));
        assertTrue(response.contains("@harumi_rui"));
        assertTrue(response.contains("@wheyshima"));
        assertTrue(response.contains("Благодарности"));
        assertTrue(response.contains("С любовью к осознанности"));
    }

    @Test
    void commandNameAndDescriptionShouldBeCorrect() {
        AuthorsCommand command = new AuthorsCommand();
        assertEquals("authors", command.getBotCommand().getCommand());
        assertEquals("my CREATORS", command.getDescription());
    }
}
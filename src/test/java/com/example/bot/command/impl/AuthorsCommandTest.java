package com.example.bot.command.impl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthorsCommandTest {

    @Test
    void shouldReturnAuthorsInfo() {
        AuthorsCommand command = new AuthorsCommand();
        String response = command.execute(null); // Message не используется

        assertTrue(response.contains("Авторы проекта"));
        assertTrue(response.contains("harumiRui"));
        assertTrue(response.contains("angrycoke"));
        assertTrue(response.startsWith("*Авторы проекта*")); // проверяем Markdown
    }
}
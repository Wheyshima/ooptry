package com.example.bot.command.impl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AboutCommandTest {

    @Test
    void shouldReturnBotDescription() {
        AboutCommand command = new AboutCommand();
        String response = command.execute(null); // Message не используется

        assertTrue(response.contains("Information о боте"));
        assertTrue(response.contains("спасение от деменции"));
        assertTrue(response.contains("Save your thinks"));
        assertTrue(response.contains("Hola"));
        assertTrue(response.startsWith("*Information о боте*"));
    }
}

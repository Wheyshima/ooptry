package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StartCommandTest {

    @Test
    public void shouldReturnCorrectStartMessage() {
        StartCommand command = new StartCommand();
        String response = command.execute(null); // ✅ null вместо mock!

        assertTrue(response.contains("Привет!"));
        assertTrue(response.contains("Умный помощник для заметок"));
        assertTrue(response.contains("used /help для просмотра"));
        assertTrue(response.contains("*Умный помощник"));
    }

    @Test
    public void commandNameAndDescriptionShouldBeCorrect() {
        StartCommand command = new StartCommand();
        assertEquals("start", command.getCommandIdentifier());      // ✅ assertEquals вместо assertTrue
        assertEquals("Запуск бота", command.getDescription());
    }
}
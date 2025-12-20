package com.example.bot.command.impl;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class StartCommandTest {

    @Test
    public void shouldReturnCorrectStartMessage() {
        StartCommand command = new StartCommand();
        String response = command.execute(mock(Message.class));

        assertTrue(response.contains("BestDay"));
        assertTrue(response.contains("помощник"));
        assertTrue(response.contains("/help"));
    }

    @Test
    public void commandNameAndDescriptionShouldBeCorrect() {
        StartCommand command = new StartCommand();
        assertEquals("start", command.getBotCommand().getCommand());
        assertEquals("Запуск бота если ВЫ забыли", command.getDescription());
    }
}
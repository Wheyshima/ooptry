package com.example.bot.command.impl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AboutCommandTest {

    @Test
    void shouldReturnBotDescription() {
        AboutCommand command = new AboutCommand();
        String response = command.execute(null);

        assertTrue(response.contains("BestDay"));
        assertTrue(response.contains("создавай свой лучший день"));
        assertTrue(response.contains("проводник"));
        assertTrue(response.contains("осознанной продуктивности"));
        assertTrue(response.contains("Видеть"));
        assertTrue(response.contains("Чувствовать"));
        assertTrue(response.contains("Действовать"));
        assertTrue(response.contains("Расти"));
        assertTrue(response.contains("/help"));
    }

    @Test
    void commandNameAndDescriptionShouldBeCorrect() {
        AboutCommand command = new AboutCommand();
        assertEquals("about", command.getBotCommand().getCommand());
        assertEquals("про ME", command.getDescription());
    }
}
package com.example.bot.command.impl;

import com.example.bot.command.CommandRegistry;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HelpCommandTest {

    private CommandRegistry createTestRegistry() {
        CommandRegistry registry = new CommandRegistry();
        registry.registerCommand(new StartCommand());
        registry.registerCommand(new HelpCommand(registry));
        registry.registerCommand(new AuthorsCommand());
        registry.registerCommand(new AboutCommand());
        return registry;
    }

    @Test
    void shouldReturnAllCommandsWhenNoArgument() {
        CommandRegistry registry = createTestRegistry();
        HelpCommand command = new HelpCommand(registry);

        Message mockMessage = mock(Message.class);
        when(mockMessage.hasText()).thenReturn(true);
        when(mockMessage.getText()).thenReturn("/help"); // без аргумента

        String response = command.execute(mockMessage);

        assertTrue(response.contains("Доступные команды"));
        assertTrue(response.contains("/start"));
        assertTrue(response.contains("/help"));
        assertTrue(response.contains("/authors"));
        assertTrue(response.contains("/about"));
    }

    @Test
    void shouldReturnSpecificHelpForStartCommand() {
        CommandRegistry registry = createTestRegistry();
        HelpCommand command = new HelpCommand(registry);

        Message mockMessage = mock(Message.class);
        when(mockMessage.hasText()).thenReturn(true);
        when(mockMessage.getText()).thenReturn("/help start");

        String response = command.execute(mockMessage);

        assertTrue(response.contains("Команда /start"));
        assertTrue(response.contains("Запуск бота"));
    }
}
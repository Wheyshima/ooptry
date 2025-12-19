package com.example.bot.command.impl;

import com.example.bot.command.CommandRegistry;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class HelpCommandTest {

    @Test
    void shouldReturnAllCommandsWhenNoArgument() {
        CommandRegistry registry = new CommandRegistry();
        HelpCommand command = new HelpCommand(registry);

        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/help");

        String response = command.execute(message);

        assertTrue(response.contains("Доступные команды"));
        assertTrue(response.contains("/help"));
    }

    @Test
    void shouldReturnSpecificHelpForCommand() {
        CommandRegistry registry = new CommandRegistry();
        registry.registerCommand(new StartCommand());
        HelpCommand command = new HelpCommand(registry);

        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/help start");

        String response = command.execute(message);

        assertTrue(response.contains("Команда /start"));
    }

    @Test
    void shouldHandleUnknownCommand() {
        CommandRegistry registry = new CommandRegistry();
        HelpCommand command = new HelpCommand(registry);

        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/help unknown");

        String response = command.execute(message);

        assertTrue(response.contains("не найдена"));
    }
}
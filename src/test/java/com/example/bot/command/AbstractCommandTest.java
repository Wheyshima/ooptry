
package com.example.bot.command;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


// Тесты для абстрактного класса команд

class AbstractCommandTest {

    // Тестовая реализация для тестирования AbstractCommand
    static class TestCommand extends AbstractCommand {
        public TestCommand() {
            super("test", "Test command");
        }

        @Override
        public String execute(Message message) {
            return "Test response";
        }
    }

    @Test
    void testAbstractCommandCreation() {
        // Given & When
        TestCommand command = new TestCommand();

        // Then
        assertNotNull(command);
        assertEquals("test", command.getBotCommand().getCommand());
        assertEquals("Test command", command.getDescription());
    }

    @Test
    void testCanExecuteRecognizesCorrectCommand() {
        // Given
        TestCommand command = new TestCommand();
        Message message = createMessageWithText("/test");

        // When & Then
        assertTrue(command.canExecute(message));
    }

    @Test
    void testCanExecuteRejectsWrongCommand() {
        // Given
        TestCommand command = new TestCommand();
        Message message = createMessageWithText("/other");

        // When & Then
        assertFalse(command.canExecute(message));
    }

    @Test
    void testCanExecuteRejectsMessageWithoutText() {
        // Given
        TestCommand command = new TestCommand();
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(false);

        // When & Then
        assertFalse(command.canExecute(message));
    }

    @Test
    void testGetCommandArgument() {
        // Given
        TestCommand command = new TestCommand();
        Message message = createMessageWithText("/test argument1 argument2");

        // When
        String argument = command.getCommandArgument(message);

        // Then
        assertEquals("argument1 argument2", argument);
    }

    @Test
    void testGetCommandArgumentNoArguments() {
        // Given
        TestCommand command = new TestCommand();
        Message message = createMessageWithText("/test");

        // When
        String argument = command.getCommandArgument(message);

        // Then
        assertEquals("", argument);
    }

    @Test
    void testBotCommandCreation() {
        // Given
        TestCommand command = new TestCommand();

        // When
        BotCommand botCommand = command.getBotCommand();

        // Then
        assertNotNull(botCommand);
        assertEquals("test", botCommand.getCommand());
        assertEquals("Test command", botCommand.getDescription());
    }

    @Test
    void testGetDetailedHelp() {
        TestCommand command = new TestCommand();

        String detailedHelp = command.getDetailedHelp();

        assertTrue(detailedHelp.contains("Команда /test"));
        assertTrue(detailedHelp.contains("Test command"));
        assertTrue(detailedHelp.contains("Быстрый доступ"));
    }

    private Message createMessageWithText(String text) {
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        return message;
    }
}
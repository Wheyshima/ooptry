
package com.example.bot.command;

import com.example.bot.command.impl.AboutCommand;
import com.example.bot.command.impl.AuthorsCommand;
import com.example.bot.command.impl.HelpCommand;
import com.example.bot.command.impl.StartCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;


//Тесты для реестра команд

class CommandRegistryTest {

    private CommandRegistry commandRegistry;

    @BeforeEach
    void setUp() {
        commandRegistry = new CommandRegistry();
    }

    @Test
    void testRegisterAndRetrieveCommand() {
        // Given
        AboutCommand aboutCommand = new AboutCommand();

        // When
        commandRegistry.registerCommand(aboutCommand);
        Command retrievedCommand = commandRegistry.getCommand("about");

        // Then
        assertNotNull(retrievedCommand);
        assertEquals(aboutCommand, retrievedCommand);
    }

    @Test
    void testGetAllCommandsReturnsCorrectList() {
        // Given
        commandRegistry.registerCommand(new StartCommand());
        commandRegistry.registerCommand(new AboutCommand());
        commandRegistry.registerCommand(new AuthorsCommand());

        // When
        List<Command> commands = commandRegistry.getAllCommands();

        // Then
        assertEquals(3, commands.size());
        assertEquals("start", commands.get(0).getBotCommand().getCommand());
    }

    @Test
    void testGetBotCommandsForMenu() {
        // Given
        commandRegistry.registerCommand(new StartCommand());
        commandRegistry.registerCommand(new AboutCommand());

        // When
        List<BotCommand> botCommands = commandRegistry.getBotCommands();

        // Then
        assertEquals(2, botCommands.size());
        assertEquals("start", botCommands.get(0).getCommand());
        assertEquals("about", botCommands.get(1).getCommand());
    }

    @Test
    void testFindCommandForMessage() {
        // Given
        AboutCommand aboutCommand = new AboutCommand();
        commandRegistry.registerCommand(aboutCommand);
        Message message = createMessageWithText("/about");

        // When
        Command foundCommand = commandRegistry.findCommandForMessage(message);

        // Then
        assertNotNull(foundCommand);
        assertEquals(aboutCommand, foundCommand);
    }

    @Test
    void testFindCommandForMessageWithArguments() {
        // Given
        AboutCommand aboutCommand = new AboutCommand();
        commandRegistry.registerCommand(aboutCommand);
        Message message = createMessageWithText("/about some argument");

        // When
        Command foundCommand = commandRegistry.findCommandForMessage(message);

        // Then
        assertNotNull(foundCommand);
        assertEquals(aboutCommand, foundCommand);
    }

    @Test
    void testFindCommandReturnsNullForUnknownCommand() {
        // Given
        Message message = createMessageWithText("/unknown");

        // When
        Command foundCommand = commandRegistry.findCommandForMessage(message);

        // Then
        assertNull(foundCommand);
    }

    @Test
    void testFindCommandReturnsNullForNonCommand() {
        // Given
        Message message = createMessageWithText("just text");

        // When
        Command foundCommand = commandRegistry.findCommandForMessage(message);

        // Then
        assertNull(foundCommand);
    }

    @Test
    void testContainsCommand() {
        // Given
        commandRegistry.registerCommand(new StartCommand());

        // When & Then
        assertTrue(commandRegistry.containsCommand("start"));
        assertFalse(commandRegistry.containsCommand("unknown"));
    }

    @Test
    void testGetCommandCount() {
        // Given
        commandRegistry.registerCommand(new StartCommand());
        commandRegistry.registerCommand(new AboutCommand());

        // When
        int count = commandRegistry.getCommandCount();

        // Then
        assertEquals(2, count);
    }

    @Test
    void testCommandOrderPreservation() {
        // Given
        Command command1 = new StartCommand();
        Command command2 = new AboutCommand();
        Command command3 = new AuthorsCommand();

        // When
        commandRegistry.registerCommand(command1);
        commandRegistry.registerCommand(command2);
        commandRegistry.registerCommand(command3);

        List<Command> commands = commandRegistry.getAllCommands();

        // Then
        assertEquals(3, commands.size());
        assertEquals(command1, commands.get(0));
        assertEquals(command2, commands.get(1));
        assertEquals(command3, commands.get(2));
    }

    private Message createMessageWithText(String text) {
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        return message;
    }
}

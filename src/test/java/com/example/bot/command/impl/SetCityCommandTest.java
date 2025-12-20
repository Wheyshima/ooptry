package com.example.bot.command.impl;

import com.example.bot.database.DatabaseManager;
import com.example.bot.model.City;
import com.example.bot.service.CityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetCityCommandTest {

    private DatabaseManager mockDatabaseManager;
    private CityService mockCityService;
    private SetCityCommand setCityCommand;

    @BeforeEach
    void setUp() {
        mockDatabaseManager = mock(DatabaseManager.class);
        mockCityService = mock(CityService.class);
        setCityCommand = new SetCityCommand(mockDatabaseManager, mockCityService);
    }

    private Message createMessage(Long userId, String text) {
        Message message = mock(Message.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(message.getFrom()).thenReturn(user);
        when(message.getText()).thenReturn(text);
        return message;
    }

    @Test
    void execute_emptyArgument_noCitySet_showsPrompt() {
        // GIVEN
        Long userId = 123L;
        Message message = createMessage(userId, "/setcity");
        when(mockDatabaseManager.getUserCity(userId)).thenReturn(null);

        // WHEN
        String result = setCityCommand.execute(message);

        // THEN
        assertTrue(result.contains("У вас пока не установлен город"));
        assertTrue(result.contains("/setcity Москва"));
    }

    @Test
    void execute_emptyArgument_cityAlreadySet_showsCurrentCity() {
        // GIVEN
        Long userId = 456L;
        Message message = createMessage(userId, "/setcity");
        when(mockDatabaseManager.getUserCity(userId)).thenReturn("Екатеринбург");

        // WHEN
        String result = setCityCommand.execute(message);

        // THEN
        assertTrue(result.contains("Ваш текущий город: *Екатеринбург*"));
    }

    @Test
    void execute_validCityName_cityFound_savesAndReturnsSuccess() {
        // GIVEN
        Long userId = 789L;
        Message message = createMessage(userId, "/setcity Москва");
        City matchedCity = new City("Москва", "Москва", 12_600_000L, 55.7558, 37.6176);
        when(mockCityService.findCity("Москва")).thenReturn(matchedCity);

        // WHEN
        String result = setCityCommand.execute(message);

        // THEN
        verify(mockDatabaseManager).updateUserCity(userId, "Москва");
        assertTrue(result.contains("✅ Город успешно установлен:\n*Москва*"));
        assertTrue(result.contains("регион: Москва"));
    }

    @Test
    void execute_invalidCityName_cityNotFound_returnsErrorMessage() {
        // GIVEN
        Long userId = 101L;
        Message message = createMessage(userId, "/setcity Абракадабра");
        when(mockCityService.findCity("Абракадабра")).thenReturn(null);

        // WHEN
        String result = setCityCommand.execute(message);

        // THEN
        verify(mockDatabaseManager, never()).updateUserCity(anyLong(), anyString());
        assertTrue(result.contains("❌ Город не найден в России"));
        assertTrue(result.contains("Пример: `/setcity Новосибирск`"));
    }

    @Test
    void getDetailedHelp_returnsCorrectMarkdown() {
        // WHEN
        String help = setCityCommand.getDetailedHelp();
        System.out.println(help);

        // THEN
        assertTrue(help.contains("*🏙 Команда /setcity - Установка или просмотр вашего города*"));
        assertTrue(help.contains("`/setcity <название>`"));
        assertTrue(help.contains("Поддерживается нечёткий поиск"));
    }

    @Test
    void commandMetadata_isCorrect() {
        assertEquals("setcity", setCityCommand.getBotCommand().getCommand());
        assertEquals("установить или посмотреть ваш город в России", setCityCommand.getDescription());
    }
}
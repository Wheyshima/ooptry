package com.example.bot.command.impl;

import com.example.bot.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;


import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

class WishlistCommandTest {

    private DatabaseManager mockDatabaseManager;
    private WishlistCommand wishlistCommand;
    private Message mockMessage;

    @BeforeEach
    void setUp() {
        // Инициализация моков и команды перед каждым тестом
        mockDatabaseManager = Mockito.mock(DatabaseManager.class);
        wishlistCommand = new WishlistCommand(mockDatabaseManager);

        mockMessage = Mockito.mock(Message.class);
        User mockUser = Mockito.mock(User.class);

        when(mockMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(12345L);
    }

    // ============ Тесты для /wishlist (просмотр желаний) ============
    @Test
    void testEnvironment() {
        System.out.println("Работаем в: " + System.getProperty("os.name"));
        System.out.println("Путь к Java: " + System.getProperty("java.home"));
    }
    @Test
    void execute_emptyCommand_withWishes_showsWishlist() {
        // Проверяет отображение списка желаний с правильной нумерацией и статусами
        when(mockMessage.getText()).thenReturn("/wishlist");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(false);

        List<DatabaseManager.Wish> wishes = Arrays.asList(
                new DatabaseManager.Wish(1, "Желание 1", true, LocalDateTime.now()),
                new DatabaseManager.Wish(2, "Желание 2", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getWishes(12345L)).thenReturn(wishes);

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🌟 *Ваша карта желаний:*"));
        assertTrue(result.contains("✅ [#1] Желание 1")); // Выполнено
        assertTrue(result.contains("🎯 [#2] Желание 2")); // Не выполнено
        assertTrue(result.contains("🔓 *Добавление разрешено*"));
        assertTrue(result.contains("✅ Завершить: `/wishlist complete <ID>`"));
    }

    @Test
    void execute_emptyCommand_emptyWishlist_unlocked_showsAddPrompt() {
        // Проверяет сообщение при пустом списке желаний, когда добавление разрешено
        when(mockMessage.getText()).thenReturn("/wishlist");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(false);
        when(mockDatabaseManager.getWishes(12345L)).thenReturn(Collections.emptyList());

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🌟 *Карта желаний пуста*"));
        assertTrue(result.contains("Добавьте новое желание:"));
        assertTrue(result.contains("`/wishlist add <ваше желание>`"));
    }

    @Test
    void execute_emptyCommand_emptyWishlist_locked_showsLockedMessage() {
        // Проверяет сообщение при пустом списке желаний, когда добавление заблокировано
        when(mockMessage.getText()).thenReturn("/wishlist");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(true);
        when(mockDatabaseManager.getWishes(12345L)).thenReturn(Collections.emptyList());

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🌟 *Карта желаний пуста*"));
        assertTrue(result.contains("🔒 Добавление новых желаний заблокировано"));
        assertTrue(result.contains("Используйте `/wishlist status` для информации"));
    }

    // ============ Тесты для /wishlist add ============

    @Test
    void execute_addCommand_validWish_addsWishSuccessfully() {
        // Проверяет успешное добавление корректного желания
        when(mockMessage.getText()).thenReturn("/wishlist add Новое желание");
        when(mockDatabaseManager.addWish(12345L, "Новое желание")).thenReturn(1);

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("✨ *Желание добавлено!*"));
        assertTrue(result.contains("📝 Текст: Новое желание"));
        assertTrue(result.contains("используйте:\n`/wishlist endadd`"));
    }

    @Test
    void execute_addCommand_emptyText_showsError() {
        // Проверяет обработку пустого текста желания
        when(mockMessage.getText()).thenReturn("/wishlist add ");

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("❌ Текст желания не может быть пустым"));
    }

    @Test
    void execute_addCommand_tooShortText_showsError() {
        // Проверяет обработку слишком короткого текста (менее 2 символов)
        when(mockMessage.getText()).thenReturn("/wishlist add A");

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("❌ Текст желания слишком короткий (минимум 2 символа)"));
    }

    @Test
    void execute_addCommand_tooLongText_showsError() {
        // Проверяет обработку слишком длинного текста (более 1000 символов)
        String longText = "A".repeat(1001);
        when(mockMessage.getText()).thenReturn("/wishlist add " + longText);

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("❌ Текст желания слишком длинный (максимум 1000 символов)"));
    }

    // ============ Тесты для /wishlist complete ============

    @Test
    void execute_completeCommand_validIndex_completesWish() {
        // Проверяет успешное завершение желания по порядковому номеру
        when(mockMessage.getText()).thenReturn("/wishlist complete 1");
        List<DatabaseManager.Wish> wishes = Collections.singletonList(
                new DatabaseManager.Wish(10, "Желание", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getWishes(12345L)).thenReturn(wishes);
        when(mockDatabaseManager.completeWish(12345L, 10)).thenReturn(true);

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🎉 *Желание #1 отмечено выполненным!*"));
        assertTrue(result.contains("✨ Вы сделали это! Вселенная отмечает вашу победу!"));
    }

    @Test
    void execute_completeCommand_invalidIndex_showsError() {
        // Проверяет обработку неверного номера желания (несуществующий индекс)
        when(mockMessage.getText()).thenReturn("/wishlist complete 5");
        when(mockDatabaseManager.getWishes(12345L)).thenReturn(
                Collections.singletonList(new DatabaseManager.Wish(1, "Желание", false, LocalDateTime.now()))
        );

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("❌ Неверный номер желания. У вас всего 1 желаний."));
    }

    @Test
    void execute_completeCommand_invalidFormat_showsError() {
        // Проверяет обработку неверного формата номера (не число)
        when(mockMessage.getText()).thenReturn("/wishlist complete abc");

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("❌ Неверный формат ID желания. Используйте: `/wishlist complete <число>`"));
    }

    // ============ Тесты для /wishlist endadd ============

    @Test
    void execute_endAddCommand_withWishes_locksWishlist() {
        // Проверяет успешную блокировку списка желаний, когда есть хотя бы одно желание
        when(mockMessage.getText()).thenReturn("/wishlist endadd");
        when(mockDatabaseManager.getWishCount(12345L)).thenReturn(2);
        when(mockDatabaseManager.getLockUntil(12345L)).thenReturn(LocalDateTime.now().plusDays(60));

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🎉 *Карта желаний сохранена и заблокирована!*"));
        assertTrue(result.contains("📊 Всего желаний: 2"));
        assertTrue(result.contains("🔒 Теперь вы не можете добавлять новые желания"));
        verify(mockDatabaseManager).lockWishlist(12345L);
    }

    @Test
    void execute_endAddCommand_emptyWishlist_showsError() {
        // Проверяет обработку попытки блокировки пустого списка желаний
        when(mockMessage.getText()).thenReturn("/wishlist endadd");
        when(mockDatabaseManager.getWishCount(12345L)).thenReturn(0);

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("❌ Нельзя заблокировать пустой список желаний!"));
        assertTrue(result.contains("Сначала добавьте хотя бы одно желание:"));
    }

    // ============ Тесты для /wishlist status ============

    @Test
    void execute_statusCommand_locked_showsLockInfo() {

        // Проверяет отображение статуса при активной блокировке
        when(mockMessage.getText()).thenReturn("/wishlist status");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(true);
        when(mockDatabaseManager.getLockUntil(12345L)).thenReturn(LocalDateTime.now().plusDays(30));

        String result = wishlistCommand.execute(mockMessage);
        System.out.println(result);
        assertTrue(result.contains("🔒 *Добавление желаний заблокировано!*"));
        assertTrue(result.contains("Осталось дней: "));
    }

    @Test
    void execute_statusCommand_unlocked_showsUnlockedInfo() {
        // Проверяет отображение статуса при отсутствии блокировки
        when(mockMessage.getText()).thenReturn("/wishlist status");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(false);
        when(mockDatabaseManager.getWishCount(12345L)).thenReturn(3);

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🔓 *Добавление желаний разрешено*"));
        assertTrue(result.contains("📊 Текущее количество желаний: 3"));
    }

    // ============ Тесты блокировки ============

    @Test
    void execute_addCommand_whenLocked_showsLockedMessage() {
        // Проверяет, что добавление желаний запрещено при активной блокировке
        when(mockMessage.getText()).thenReturn("/wishlist add Новое желание");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(true);
        when(mockDatabaseManager.getLockUntil(12345L)).thenReturn(LocalDateTime.now().plusDays(10));

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🔒 *Добавление желаний заблокировано!*"));
        assertTrue(result.contains("Осталось дней: "));
    }

    @Test
    void execute_endAddCommand_whenLocked_showsLockedMessage() {
        // Проверяет, что команда endadd недоступна при активной блокировке
        when(mockMessage.getText()).thenReturn("/wishlist endadd");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(true);
        when(mockDatabaseManager.getLockUntil(12345L)).thenReturn(LocalDateTime.now().plusDays(5));

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🔒 *Добавление желаний заблокировано!*"));
        assertTrue(result.contains("Осталось дней: "));
    }

    // ============ Тесты для разрешенных команд при блокировке ============

    @Test
    void execute_completeCommand_whenLocked_allowsCompletion() {
        // Проверяет, что завершение желаний разрешено даже при блокировке
        when(mockMessage.getText()).thenReturn("/wishlist complete 1");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(true);
        List<DatabaseManager.Wish> wishes = Collections.singletonList(
                new DatabaseManager.Wish(10, "Желание", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getWishes(12345L)).thenReturn(wishes);
        when(mockDatabaseManager.completeWish(12345L, 10)).thenReturn(true);

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🎉 *Желание #1 отмечено выполненным!*"));
    }

    @Test
    void execute_statusCommand_whenLocked_allowsStatusCheck() {
        // Проверяет, что проверка статуса разрешена при блокировке
        when(mockMessage.getText()).thenReturn("/wishlist status");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(true);
        when(mockDatabaseManager.getLockUntil(12345L)).thenReturn(LocalDateTime.now().plusDays(15));

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🔒 *Добавление желаний заблокировано!*"));
        assertTrue(result.contains("Осталось дней: "));
    }

    @Test
    void execute_emptyCommand_whenLocked_allowsViewing() {
        // Проверяет, что просмотр списка желаний разрешен при блокировке
        when(mockMessage.getText()).thenReturn("/wishlist");
        when(mockDatabaseManager.isWishlistLocked(12345L)).thenReturn(true);
        when(mockDatabaseManager.getLockUntil(12345L)).thenReturn(LocalDateTime.now().plusDays(5));
        List<DatabaseManager.Wish> wishes = Collections.singletonList(
                new DatabaseManager.Wish(1, "Желание", false, LocalDateTime.now())
        );
        when(mockDatabaseManager.getWishes(12345L)).thenReturn(wishes);

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🌟 *Ваша карта желаний:*"));
        assertTrue(result.contains("🎯 [#1] Желание"));
        assertTrue(result.contains("🔒 *Блокировка активна*"));
    }

    // ============ Тесты для неверных команд ============

    @Test
    void execute_invalidCommand_showsUsage() {
        // Проверяет отображение справки при неизвестной команде
        when(mockMessage.getText()).thenReturn("/wishlist invalid");

        String result = wishlistCommand.execute(mockMessage);

        assertTrue(result.contains("🎯 *Управление картой желаний:*"));
        assertTrue(result.contains("`/wishlist add <текст>`"));
        assertTrue(result.contains("`/wishlist endadd`"));
    }

    // ============ Тесты описания команды ============

    @Test
    void commandNameAndDescriptionShouldBeCorrect() {
        // Проверяет корректность имени команды и её описания для регистрации в Telegram
        assertEquals("wishlist", wishlistCommand.getBotCommand().getCommand());
        assertEquals("Управление картой желаний", wishlistCommand.getDescription());
    }
}
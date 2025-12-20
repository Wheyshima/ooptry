// com.example.bot.keyboard/InlineKeyboardFactoryTest.java
package com.example.bot.keyboard;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InlineKeyboardFactoryTest {

    @Test
    void getTodoActionsKeyboard_returnsFourButtonsInTwoRows() {
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.getTodoActionsKeyboard();
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).size());
        assertEquals(2, rows.get(1).size());

        assertEquals("➕ Добавить задачу", rows.get(0).getFirst().getText());
        assertEquals("todo:add", rows.get(0).getFirst().getCallbackData());

        assertEquals("🔄 Обновить список", rows.get(1).get(1).getText());
        assertEquals("todo:refresh", rows.get(1).get(1).getCallbackData());
    }

    @Test
    void getWishlistActionsKeyboard_whenUnlockedAndHasWishes_returnsAllButtons() {
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.getWishlistActionsKeyboard(false, true);
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

        assertEquals(4, rows.size()); // add, complete, endadd, refresh
        assertEquals("➕ Добавить желание", rows.get(0).getFirst().getText());
        assertEquals("🔒 Завершить добавление", rows.get(2).getFirst().getText());
    }

    @Test
    void getWishlistActionsKeyboard_whenLocked_returnsOnlyCompleteAndRefresh() {
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.getWishlistActionsKeyboard(true, true);
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

        assertEquals(2, rows.size()); // complete, refresh (no add, no endadd)
        assertEquals("✅ Завершить желание", rows.get(0).getFirst().getText());
        assertEquals("🔄 Обновить список", rows.get(1).getFirst().getText());
    }

    @Test
    void getWishlistActionsKeyboard_whenNoWishes_returnsOnlyAddAndRefresh() {
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.getWishlistActionsKeyboard(false, false);
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

        assertEquals(2, rows.size()); // only add, refresh (no complete, no endadd)
        assertEquals("➕ Добавить желание", rows.get(0).getFirst().getText());
        assertEquals("🔄 Обновить список", rows.get(1).getFirst().getText());
    }

    @Test
    void getChangeCityConfirmationKeyboard_returnsYesNoButtons() {
        InlineKeyboardMarkup keyboard = InlineKeyboardFactory.getChangeCityConfirmationKeyboard();
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

        assertEquals(1, rows.size());
        assertEquals(2, rows.getFirst().size());
        assertEquals("Да", rows.getFirst().getFirst().getText());
        assertEquals("change_city_yes", rows.getFirst().getFirst().getCallbackData());
    }
}
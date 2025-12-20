package com.example.bot.keyboard;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InlineKeyboardFactory {

    public static InlineKeyboardMarkup getTodoActionsKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(Arrays.asList(
                        Arrays.asList(
                                InlineKeyboardButton.builder().text("➕ Добавить задачу").callbackData("todo:add").build(),
                                InlineKeyboardButton.builder().text("✅ Завершить задачу").callbackData("todo:complete").build()
                        ),
                        Arrays.asList(
                                InlineKeyboardButton.builder().text("✏️ Редактировать задачу").callbackData("todo:edit").build(),
                                InlineKeyboardButton.builder().text("🔄 Обновить список").callbackData("todo:refresh").build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup getWishlistActionsKeyboard(boolean isLocked, boolean hasWishes) {
        var rows = new java.util.ArrayList<List<InlineKeyboardButton>>();

        if (!isLocked) {
            rows.add(Collections.singletonList(
                    InlineKeyboardButton.builder().text("➕ Добавить желание").callbackData("wishlist:add").build()
            ));
        }

        if (hasWishes) {
            rows.add(Collections.singletonList(
                    InlineKeyboardButton.builder().text("✅ Завершить желание").callbackData("wishlist:complete").build()
            ));
        }

        if (!isLocked && hasWishes) {
            rows.add(Collections.singletonList(
                    InlineKeyboardButton.builder().text("🔒 Завершить добавление").callbackData("wishlist:endadd").build()
            ));
        }

        rows.add(Collections.singletonList(
                InlineKeyboardButton.builder().text("🔄 Обновить список").callbackData("wishlist:refresh").build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup getChangeCityConfirmationKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        Arrays.asList(
                                InlineKeyboardButton.builder().text("Да").callbackData("change_city_yes").build(),
                                InlineKeyboardButton.builder().text("Нет").callbackData("change_city_no").build()
                        )
                ))
                .build();
    }

    public static InlineKeyboardMarkup getWeekStatsKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        Collections.singletonList(
                                InlineKeyboardButton.builder().text("📈 Недельная статистика").callbackData("stats:week").build()
                        )
                ))
                .build();
    }
}
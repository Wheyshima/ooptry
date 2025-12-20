// com.example.bot.service.KeyboardService
package com.example.bot.service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

public class KeyboardService {

    public static ReplyKeyboardMarkup mainMenu() {
        KeyboardRow row1 = new KeyboardRow();
        row1.add("/todo");
        row1.add("/stats");
        row1.add("/wishlist");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("/start");
        row2.add("/help");
        row2.add("/setcity");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("/about");
        row3.add("/authors");

        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2, row3))
                .resizeKeyboard(true)
                .oneTimeKeyboard(false)
                .build();
    }
}
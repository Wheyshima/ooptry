package com.example.bot.command.impl;

import com.example.bot.command.AbstractCommand;
import com.example.bot.database.DatabaseManager;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class WishlistCommand extends AbstractCommand {
    private final DatabaseManager databaseManager;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public WishlistCommand(DatabaseManager databaseManager) {
        super("wishlist", "Управление картой желаний");
        this.databaseManager = databaseManager;
    }

    @Override
    public String execute(Message message) {
        String argument = getCommandArgument(message); // ← НЕ вызываем .trim() здесь!
        Long userId = message.getFrom().getId();

        // Проверяем блокировку
        if (databaseManager.isWishlistLocked(userId)) {
            if (getCommandAction(argument).equals("complete") ||
                    argument.equals("status") ||
                    argument.isEmpty()) {
                // Разрешено
            } else {
                return getLockedMessage(userId);
            }
        }

        if (argument.isEmpty()) {
            return showWishes(userId);
        }

        return switch (getCommandAction(argument)) {
            case "add" -> handleAddWish(userId, getActionArgument(argument, "add"));
            case "complete" -> handleCompleteWish(userId, getActionArgument(argument, "complete"));
            case "endadd" -> endAddWishes(userId);
            case "status" -> getLockStatus(userId);
            default -> getUsage();
        };
    }
    /**
     * Извлекает аргумент действия (текст после команды)
     */
    private String getActionArgument(String argument, String action) {
        if (argument.startsWith(action + " ")) {
            return argument.substring((action + " ").length()).trim();
        }
        return ""; // если нет аргумента (например, "/wishlist add" без текста)
    }
    private String getCommandAction(String argument) {
        if (argument.startsWith("add ") || argument.equals("add")) return "add";
        if (argument.startsWith("complete ")) return "complete";
        if (argument.equals("endadd")) return "endadd";
        if (argument.equals("status")) return "status";
        return "unknown";
    }
    private String handleAddWish(Long userId, String wishText) {
        if (wishText.isEmpty()) {
            return "❌ Текст желания не может быть пустым";
        }
        if (wishText.length() > 1000) {
            return "❌ Текст желания слишком длинный (максимум 1000 символов)";
        }
        if (wishText.length() < 2) {
            return "❌ Текст желания слишком короткий (минимум 2 символа)";
        }
        return addWish(userId, wishText);
    }

    private String handleCompleteWish(Long userId, String taskIdArg) {
        try {
            int displayIndex = Integer.parseInt(taskIdArg.trim());
            return completeWish(userId, displayIndex);
        } catch (NumberFormatException e) {
            return "❌ Неверный формат ID желания. Используйте: `/wishlist complete <число>`";
        }
    }

    @Override
    public String getDetailedHelp() {
        return """
            🌟 *команда /wishlist - карта ваших судьбоносных целей* 
            
            🎯 *сакральное пространство ваших намерений*
            здесь рождаются и фиксируются ваши самые сокровенные желания,
            становясь частью ткани мироздания.
        
            ⚡ *божественные правила:*
            • ❌ желания неизменяемы и неудаляемы
            • ✅ исполненные желания отмечаются, но не исчезают
            • 🔒 возможность временной блокировки для концентрации
        
            📜 *философия неизменности:*
            "каждая запись - это диалог со вселенной.
            'ошибка' может быть божественным знамением.
            изменение = отречение от изначального импульса души.
            вы берете ответственность за каждое произнесенное слово."
        
            🔄 *обновленная система работы:*
        
            *📝 создание желаний (фаза 1):*
            `/wishlist add <желание>` - запечатлеть новое стремление
        
            *🔒 фокусировка (фаза 2):*
            `/wishlist endadd` - запечатать карту на 2 лунных цикла
            • новые желания временно недоступны
            • концентрация на существующих целях
            • автоматическое обновление через 2 месяца
        
            *✅ отслеживание (всегда доступно):*
            /wishlist - созерцать карту предначертаний
            `/wishlist complete <ID>` - отметить исполнение желания
            `/wishlist stats` - статистика вашего духовного пути
        
            🌌 *примеры сакральных формулировок:*
            • `/wishlist add найти свое предназначение до конца года`
            • `/wishlist add пробудить творческую энергию вселенной`
        
            📊 *новые возможности:*
            • 🏆 система отметок исполнения (без удаления)
            • 🌙 циклическая система фокусировки (2 месяца)
        
            🔮 *мудрость системы:*
            "то, что однажды было вписано в карту желаний -
            уже стало частью вашей кармы. отмечая исполнение,
            вы признаете диалог со вселенной завершенным."
        
            🎭 *сценарии использования:*
            1. *фаза накопления* - свободное добавление желаний
            2. *фаза концентрации* - блокировка и работа с существующим
            3. *фаза обновления* - автоматическое обновление цикла
        
            📈 *команды для глубокой работы:*
            /wishlist - основная карта с визуализацией
            `/wishlist endadd` - хронология вашего развития
            `/wishlist completed` - галерея ваших побед
            `/wishlist stats` - текущие вызовы вселенной
        
            💫 *начните духовный путь:*
            напишите /wishlist чтобы прикоснуться к своей судьбе
            или сразу начните с первого желания:
            `/wishlist add <ваше самое сокровенное стремление>`
        
            🌙 *помните:* карта желаний - это живой диалог со вселенной,
            где каждое слово имеет вес, а каждое исполненное желание -
            новый уровень вашей духовной эволюции.
        """;
    }

    private String showWishes(Long userId) {
        List<DatabaseManager.Wish> wishes = databaseManager.getWishes(userId);
        boolean isLocked = databaseManager.isWishlistLocked(userId);

        if (wishes.isEmpty()) {
            String message = "🌟 *Карта желаний пуста*\n\n";
            if (!isLocked) {
                message += "Добавьте новое желание:\n`/wishlist add <ваше желание>`";
            } else {
                message += "🔒 Добавление новых желаний заблокировано\nИспользуйте `/wishlist status` для информации";
            }
            return message;
        }

        StringBuilder sb = new StringBuilder("🌟 *Ваша карта желаний:*\n\n");

        int displayIndex = 1;
        for (DatabaseManager.Wish wish : wishes) {
            String status = wish.isCompleted() ? "✅" : "🎯";
            sb.append(String.format("%s [#%d] %s\n", status, displayIndex, wish.getText()));
            displayIndex++;
        }

        // Добавляем информацию о блокировке
        if (isLocked) {
            LocalDateTime lockUntil = databaseManager.getLockUntil(userId);
            if (lockUntil != null) {
                long daysLeft = java.time.Duration.between(LocalDateTime.now(), lockUntil).toDays();
                sb.append(String.format("\n🔒 *Блокировка активна* (%d дней осталось)", daysLeft));
            }
        } else {
            sb.append("\n🔓 *Добавление разрешено*");
        }

        // Добавляем подсказки по действиям
        sb.append("\n\n🔧 *Действия:*");
        sb.append("\n✅ Завершить: `/wishlist complete <ID>`");
        if (!isLocked) {
            sb.append("\n⭐ Добавить новое: `/wishlist add <текст>`");
            sb.append("\n🔒 Завершить добавление: `/wishlist endadd`");
        }
        sb.append("\n📊 Статус: `/wishlist status`");
        if (!isLocked) {
            sb.append("\n\n⚠️ *Внимание:* Не заблокированные желания удаляются каждую ночь!");
            sb.append("\nИспользуйте `/wishlist endadd` чтобы сохранить их на 60 дней");
        }
        return sb.toString();
    }

    private String addWish(Long userId, String wishText) {
        int wishId = databaseManager.addWish(userId, wishText);
        if (wishId != -1) {
            return "✨ *Желание добавлено!*\n\n" +
                    "📝 Текст: " + wishText + "\n\n" +
                    "💡 Когда закончите добавлять, используйте:\n`/wishlist endadd`";
        }
        return "❌ Ошибка добавления желания";
    }

    private String completeWish(Long userId, int displayIndex) {
        List<DatabaseManager.Wish> wishes = databaseManager.getWishes(userId);

        if (displayIndex < 1 || displayIndex > wishes.size()) {
            return "❌ Неверный номер желания. У вас всего " + wishes.size() + " желаний.";
        }

        DatabaseManager.Wish wish = wishes.get(displayIndex - 1);
        int realWishId = wish.getId();

        if (databaseManager.completeWish(userId, realWishId)) {
            return "🎉 *Желание #" + displayIndex + " отмечено выполненным!*\n\n" +
                    "✨ Вы сделали это! Вселенная отмечает вашу победу!\n" +
                    "Продолжайте в том же духе: /wishlist";
        } else {
            return "❌ Желание #" + displayIndex + " не найдено или уже выполнено\n" +
                    "Проверьте актуальный список: /wishlist";
        }
    }

    private String getLockedMessage(Long userId) {
        LocalDateTime lockUntil = databaseManager.getLockUntil(userId);
        if (lockUntil != null) {
            long daysLeft = java.time.Duration.between(LocalDateTime.now(), lockUntil).toDays();
            return "🔒 *Добавление желаний заблокировано!*\n\n" +
                    "⏰ Срок блокировки истекает: " + lockUntil.format(formatter) + "\n" +
                    "📅 Осталось дней: " + daysLeft + "\n\n" +
                    "Вы можете:\n" +
                    "• Просматривать желания /wishlist\n" +
                    "• Отмечать выполненные `/wishlist complete <ID>`\n" +
                    "• Проверить статус `/wishlist status`";
        }
        return "🔒 Добавление желаний временно недоступно";
    }

    private String getLockStatus(Long userId) {
        if (databaseManager.isWishlistLocked(userId)) {
            return getLockedMessage(userId);
        } else {
            int wishCount = databaseManager.getWishCount(userId);
            return "🔓 *Добавление желаний разрешено*\n\n" +
                    "📊 Текущее количество желаний: " + wishCount + "\n\n" +
                    "Вы можете:\n" +
                    "• Добавлять новые: `/wishlist add <желание>`\n" +
                    "• Завершить добавление: `/wishlist endadd`\n" +
                    "• Просмотреть список: /wishlist";
        }
    }

    private String endAddWishes(Long userId) {
        int wishCount = databaseManager.getWishCount(userId);

        if (wishCount == 0) {
            return "❌ Нельзя заблокировать пустой список желаний!\n\n" +
                    "Сначала добавьте хотя бы одно желание:\n" +
                    "`/wishlist add <ваше желание>`";
        }

        // ВЫЗЫВАЕМ ФАКТИЧЕСКУЮ БЛОКИРОВКУ
        databaseManager.lockWishlist(userId);

        // Проверяем действительно ли заблокировано

        boolean isActuallyLocked = databaseManager.isWishlistLocked(userId);
        System.out.println("🔍 Проверка блокировки: " + isActuallyLocked);

        LocalDateTime lockUntil = databaseManager.getLockUntil(userId);

        return "🎉 *Карта желаний сохранена и заблокирована!*\n\n" +
                "📊 Всего желаний: " + wishCount + "\n" +
                "⏰ Срок блокировки: " + DatabaseManager.WISHLIST_LOCK_DAYS + " дней\n" +
                "📅 Разблокировка: " + (lockUntil != null ? lockUntil.format(formatter) : "через " + DatabaseManager.WISHLIST_LOCK_DAYS +" дней") + "\n\n" +

                "🔒 Теперь вы не можете добавлять новые желания\n" +
                "✅ Но можете отмечать выполненные\n" +
                "👀 Просматривать свой список\n\n" +
                "💫 *Через " +DatabaseManager.WISHLIST_LOCK_DAYS + " дней все желания будут автоматически удалены!*";
    }

    private String getUsage() {
        return """
        🎯 *Управление картой желаний:*
        
        • /wishlist - показать все желания
        • `/wishlist add <текст>` - добавить желание
        • `/wishlist endadd` - ✅ ЗАВЕРШИТЬ и заблокировать (2 месяца)
        • `/wishlist complete <ID>` - отметить выполненным
        • `/wishlist status` - статус блокировки
        
        💫 *Важно:* После `/wishlist endadd` добавление новых желаний блокируется на 2 месяца!
        """;
    }
}
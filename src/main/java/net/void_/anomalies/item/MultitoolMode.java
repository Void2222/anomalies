package net.void_.anomalies.item;

import net.minecraft.ChatFormatting;

public enum MultitoolMode {
    ANALYZE("Анализ", ChatFormatting.AQUA, "ПКМ по аномалии для вывода характеристик"),
    MODIFY("Изменение", ChatFormatting.GOLD, "ПКМ — выбор для ввода параметров в чат | Shift+ПКМ — сброс"),
    RELOCATE("Перемещение", ChatFormatting.GREEN, "ПКМ — захват | ЛКМ по блоку — перенос | Shift+ПКМ — смещение"),
    DELETE("Удаление", ChatFormatting.RED, "ЛКМ/ПКМ по аномалии — мгновенное уничтожение");

    private final String name;
    private final ChatFormatting color;
    private final String description;

    MultitoolMode(String name, ChatFormatting color, String description) {
        this.name = name;
        this.color = color;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public MultitoolMode next() {
        MultitoolMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
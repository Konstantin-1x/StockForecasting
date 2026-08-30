package org.example.parser.wb;

import java.util.Map;

public final class WildberriesCategoryNameTranslator {

    private static final Map<String, String> RUSSIAN_NAMES = Map.ofEntries(
            Map.entry("3D-печать", "3Д-печать"),
            Map.entry("DVD и медиа-плееры", "ДВД и медиаплееры"),
            Map.entry("iPhone 11", "Айфон 11"),
            Map.entry("iPhone 12", "Айфон 12"),
            Map.entry("iPhone 13", "Айфон 13"),
            Map.entry("iPhone 14", "Айфон 14"),
            Map.entry("iPhone 15", "Айфон 15"),
            Map.entry("iPhone 16", "Айфон 16"),
            Map.entry("iPhone 17", "Айфон 17"),
            Map.entry("iPhone X", "Айфон 10"),
            Map.entry("L-карнитины", "Л-карнитины"),
            Map.entry("Mesh системы", "Меш-системы"),
            Map.entry("OFFroad", "Внедорожные товары"),
            Map.entry("PoE инжекторы", "Инжекторы сетевого питания"),
            Map.entry("SIM-карты", "СИМ-карты"),
            Map.entry("USB-накопители и карты памяти", "ЮСБ-накопители и карты памяти"),
            Map.entry("Витамин D", "Витамин Д"),
            Map.entry("Жесткие диски HDD", "Жесткие диски"),
            Map.entry("Защитные стекла для телефонов Apple", "Защитные стекла для телефонов Эпл"),
            Map.entry("Защитные стекла для телефонов Honor", "Защитные стекла для телефонов Хонор"),
            Map.entry("Защитные стекла для телефонов Huawei", "Защитные стекла для телефонов Хуавей"),
            Map.entry("Защитные стекла для телефонов Infinix", "Защитные стекла для телефонов Инфиникс"),
            Map.entry("Защитные стекла для телефонов Realme", "Защитные стекла для телефонов Реалми"),
            Map.entry("Защитные стекла для телефонов Samsung", "Защитные стекла для телефонов Самсунг"),
            Map.entry("Защитные стекла для телефонов TECNO", "Защитные стекла для телефонов Текно"),
            Map.entry("Защитные стекла для телефонов Vivo", "Защитные стекла для телефонов Виво"),
            Map.entry("Защитные стекла для телефонов Xiaomi", "Защитные стекла для телефонов Сяоми"),
            Map.entry("Картины 3D", "Картины 3Д"),
            Map.entry("Конструкторы LEGO", "Конструкторы Лего"),
            Map.entry("Серия Galaxy Ax0", "Серия Галакси АХ0"),
            Map.entry("Серия Galaxy Ax2", "Серия Галакси АХ2"),
            Map.entry("Серия Galaxy Ax3", "Серия Галакси АХ3"),
            Map.entry("Серия Galaxy Ax4", "Серия Галакси АХ4"),
            Map.entry("Серия Galaxy Ax5", "Серия Галакси АХ5"),
            Map.entry("Серия Galaxy Ax6", "Серия Галакси АХ6"),
            Map.entry("Серия Galaxy S", "Серия Галакси С"),
            Map.entry("Серия Galaxy Z Fold", "Серия Галакси Зет Фолд"),
            Map.entry("Серия Mi Note", "Серия Ми Ноут"),
            Map.entry("Серия Redmi", "Серия Редми"),
            Map.entry("Серия Redmi Note", "Серия Редми Ноут"),
            Map.entry("Серия X", "Серия Икс"),
            Map.entry("Твердотельные накопители SSD", "Твердотельные накопители"),
            Map.entry("Чехлы для телефонов Apple", "Чехлы для телефонов Эпл"),
            Map.entry("Чехлы для телефонов Honor", "Чехлы для телефонов Хонор"),
            Map.entry("Чехлы для телефонов Huawei", "Чехлы для телефонов Хуавей"),
            Map.entry("Чехлы для телефонов Infinix", "Чехлы для телефонов Инфиникс"),
            Map.entry("Чехлы для телефонов Oppo", "Чехлы для телефонов Оппо"),
            Map.entry("Чехлы для телефонов Realme", "Чехлы для телефонов Реалми"),
            Map.entry("Чехлы для телефонов Samsung", "Чехлы для телефонов Самсунг"),
            Map.entry("Чехлы для телефонов TECNO", "Чехлы для телефонов Текно"),
            Map.entry("Чехлы для телефонов Xiaomi", "Чехлы для телефонов Сяоми")
    );

    private WildberriesCategoryNameTranslator() {
    }

    public static String toRussian(String categoryName) {
        if (categoryName == null) {
            return null;
        }
        return RUSSIAN_NAMES.getOrDefault(categoryName.trim(), categoryName);
    }

    public static Map<String, String> replacements() {
        return RUSSIAN_NAMES;
    }
}

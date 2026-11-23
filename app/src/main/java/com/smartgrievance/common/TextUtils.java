package com.smartgrievance.common;

import java.util.Locale;

public final class TextUtils {
    private TextUtils() {}

    public static String likeContains(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}

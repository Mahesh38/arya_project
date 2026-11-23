package com.smartgrievance.common.web;

import com.smartgrievance.common.PageRequest;

public final class AdminFilters {
    private AdminFilters() {}

    public static Boolean flag(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        return "false".equalsIgnoreCase(value) ? false : null;
    }

    public static PageRequest page(String page, String size) {
        return PageRequest.from(number(page, Integer::valueOf), number(size, Integer::valueOf));
    }

    public static Long id(String value) {
        return number(value, Long::valueOf);
    }

    private static <N> N number(String value, java.util.function.Function<String, N> read) {
        if (value == null) return null;
        try {
            return read.apply(value);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}

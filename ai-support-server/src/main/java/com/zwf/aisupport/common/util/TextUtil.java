package com.zwf.aisupport.common.util;

import java.util.Locale;

public final class TextUtil {

    private TextUtil() {
    }

    public static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public static String normalizeSearchText(String value) {
        return safeLower(value).replaceAll("\\s+", " ").trim();
    }

    public static boolean containsAny(String text, String... terms) {
        String normalized = text == null ? "" : text;
        for (String term : terms) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}

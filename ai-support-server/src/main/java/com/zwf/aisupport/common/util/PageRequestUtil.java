package com.zwf.aisupport.common.util;

public final class PageRequestUtil {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private PageRequestUtil() {
    }

    public static int page(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    public static int size(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public static int offset(int page, int size) {
        return (Math.max(1, page) - 1) * Math.max(1, size);
    }

    public static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

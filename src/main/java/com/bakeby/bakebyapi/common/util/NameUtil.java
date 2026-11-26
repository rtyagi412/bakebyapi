package com.bakeby.bakebyapi.common.util;

/** Helpers for user-supplied display names. */
public final class NameUtil {

    private NameUtil() {
    }

    /** Strips leading and trailing whitespace, including Unicode spaces such as NBSP that String.strip() keeps. */
    public static String normalize(String name) {
        return name.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
    }
}

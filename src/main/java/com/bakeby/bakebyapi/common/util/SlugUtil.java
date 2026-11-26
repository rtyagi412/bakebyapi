package com.bakeby.bakebyapi.common.util;

import com.bakeby.bakebyapi.common.exception.BadRequestException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Slug helpers: generation and validation. */
public final class SlugUtil {

    public static final String SLUG_REGEX = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    private static final Pattern SLUG_PATTERN = Pattern.compile(SLUG_REGEX);
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");
    private static final Pattern ALL_DIGITS = Pattern.compile("^[0-9]+$");

    private SlugUtil() {
    }

    /** Lowercases, replaces runs of non-alphanumerics with '-', trims leading/trailing '-'. Null gives "". */
    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String s = NON_ALNUM.matcher(input.toLowerCase(Locale.ROOT)).replaceAll("-");
        return EDGE_DASHES.matcher(s).replaceAll("");
    }

    /** True if the slug matches the slug pattern. Does not check the all-digit rule. */
    public static boolean isValidPattern(String slug) {
        return slug != null && SLUG_PATTERN.matcher(slug).matches();
    }

    /** True if the slug matches the pattern and is not all digits (numeric values are treated as ids). */
    public static boolean isValid(String slug) {
        return isValidPattern(slug) && !ALL_DIGITS.matcher(slug).matches();
    }

    /** Returns the slug if valid, otherwise throws {@link BadRequestException}. */
    public static String requireValid(String slug) {
        if (!isValid(slug)) {
            throw new BadRequestException("Invalid slug: must match " + SLUG_REGEX + " and contain a letter");
        }
        return slug;
    }
}

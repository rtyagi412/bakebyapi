package com.bakeby.bakebyapi.common.util;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolves a public {@code {idOrSlug}} value: an all-digit value is an id, anything else is a slug. Slugs
 * cannot be all digits (see {@link SlugUtil#isValid}), so the two never clash.
 */
public final class IdOrSlug {

    private static final Pattern ALL_DIGITS = Pattern.compile("^[0-9]+$");

    private IdOrSlug() {
    }

    /**
     * Looks the value up by id when it is all digits, otherwise by slug. An all-digit value too large for a
     * {@code long} cannot match any id, so it resolves to empty.
     */
    public static <T> Optional<T> resolve(String value, Function<Long, Optional<T>> byId,
                                          Function<String, Optional<T>> bySlug) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        if (ALL_DIGITS.matcher(value).matches()) {
            Long id;
            try {
                id = Long.valueOf(value);
            } catch (NumberFormatException tooLarge) {
                return Optional.empty();
            }
            return byId.apply(id);
        }
        return bySlug.apply(value);
    }
}

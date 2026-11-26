package com.bakeby.bakebyapi.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdOrSlugTests {

    final List<Long> idCalls = new ArrayList<>();
    final List<String> slugCalls = new ArrayList<>();
    Function<Long, Optional<String>> byId;
    Function<String, Optional<String>> bySlug;

    @BeforeEach
    void setUp() {
        byId = id -> {
            idCalls.add(id);
            return id == 42L ? Optional.of("by-id") : Optional.empty();
        };
        bySlug = slug -> {
            slugCalls.add(slug);
            return slug.equals("cakes") ? Optional.of("by-slug") : Optional.empty();
        };
    }

    @Test
    void allDigitValueIsLookedUpById() {
        assertThat(IdOrSlug.resolve("42", byId, bySlug)).contains("by-id");
        assertThat(idCalls).containsExactly(42L);
        assertThat(slugCalls).isEmpty();
    }

    @Test
    void nonDigitValueIsLookedUpBySlug() {
        assertThat(IdOrSlug.resolve("cakes", byId, bySlug)).contains("by-slug");
        assertThat(slugCalls).containsExactly("cakes");
        assertThat(idCalls).isEmpty();
    }

    @Test
    void unknownIdResolvesToEmpty() {
        assertThat(IdOrSlug.resolve("7", byId, bySlug)).isEmpty();
        assertThat(idCalls).containsExactly(7L);
    }

    @Test
    void unknownSlugResolvesToEmpty() {
        assertThat(IdOrSlug.resolve("pies", byId, bySlug)).isEmpty();
    }

    @Test
    void leadingZerosAreStillAnId() {
        assertThat(IdOrSlug.resolve("0042", byId, bySlug)).contains("by-id");
        assertThat(idCalls).containsExactly(42L);
    }

    @Test
    void maxLongIsLookedUpById() {
        IdOrSlug.resolve(String.valueOf(Long.MAX_VALUE), byId, bySlug);
        assertThat(idCalls).containsExactly(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775808", "99999999999999999999999999"})
    void allDigitValueOverflowingLongResolvesToEmptyWithoutLookup(String value) {
        assertThat(IdOrSlug.resolve(value, byId, bySlug)).isEmpty();
        assertThat(idCalls).isEmpty();
        assertThat(slugCalls).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "+1", "1.5", "12a", " 12", "12 ", "1e3"})
    void valuesThatAreNotPurelyDigitsAreTreatedAsSlugs(String value) {
        assertThat(IdOrSlug.resolve(value, byId, bySlug)).isEmpty();
        assertThat(idCalls).isEmpty();
        assertThat(slugCalls).containsExactly(value);
    }

    @Test
    void nonAsciiDigitsAreTreatedAsSlug() {
        // Arabic-Indic digits are not [0-9]; must not be parsed as an id.
        IdOrSlug.resolve("١٢", byId, bySlug);
        assertThat(idCalls).isEmpty();
        assertThat(slugCalls).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullOrEmptyResolvesToEmptyWithoutLookup(String value) {
        assertThat(IdOrSlug.resolve(value, byId, bySlug)).isEmpty();
        assertThat(idCalls).isEmpty();
        assertThat(slugCalls).isEmpty();
    }
}

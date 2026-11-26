package com.bakeby.bakebyapi.common.util;

import com.bakeby.bakebyapi.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugUtilTests {

    @Test
    void slugifiesTitleToLowercaseHyphenated() {
        assertThat(SlugUtil.slugify("  Chocolate Truffle Cake! ")).isEqualTo("chocolate-truffle-cake");
    }

    @Test
    void slugifyCollapsesRunsOfSymbols() {
        assertThat(SlugUtil.slugify("a  &&  b---c")).isEqualTo("a-b-c");
    }

    @Test
    void slugifyReturnsEmptyForNullAndSymbolsOnly() {
        assertThat(SlugUtil.slugify(null)).isEmpty();
        assertThat(SlugUtil.slugify("!!!")).isEmpty();
    }

    @Test
    void slugifyDoesNotTransliterateAccents() {
        assertThat(SlugUtil.slugify("Crème Brûlée")).isEqualTo("cr-me-br-l-e");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abc", "a-b", "a1", "123", "cake-2-go"})
    void acceptsValidPatterns(String slug) {
        assertThat(SlugUtil.isValidPattern(slug)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A", "a--b", "-a", "a-", "a b", "a_b", "é"})
    void rejectsInvalidPatterns(String slug) {
        assertThat(SlugUtil.isValidPattern(slug)).isFalse();
        assertThat(SlugUtil.isValid(slug)).isFalse();
    }

    @Test
    void isValidPatternIsNullSafe() {
        assertThat(SlugUtil.isValidPattern(null)).isFalse();
        assertThat(SlugUtil.isValid(null)).isFalse();
    }

    @Test
    void isValidRejectsAllDigitsButPatternAllowsThem() {
        assertThat(SlugUtil.isValidPattern("123")).isTrue();
        assertThat(SlugUtil.isValid("123")).isFalse();
        assertThat(SlugUtil.isValid("a1")).isTrue();
        assertThat(SlugUtil.isValid("1-2")).isTrue();
    }

    @Test
    void requireValidReturnsSlugWhenValid() {
        assertThat(SlugUtil.requireValid("choc-cake")).isEqualTo("choc-cake");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "Bad Slug", "", "-x"})
    void requireValidThrowsBadRequestWhenInvalid(String slug) {
        assertThatThrownBy(() -> SlugUtil.requireValid(slug)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void requireValidThrowsBadRequestForNull() {
        assertThatThrownBy(() -> SlugUtil.requireValid(null)).isInstanceOf(BadRequestException.class);
    }
}

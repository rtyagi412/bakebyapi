package com.bakeby.bakebyapi.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.dto.VariantCreateRequest;
import com.bakeby.bakebyapi.catalog.dto.VariantResponse;
import com.bakeby.bakebyapi.catalog.dto.VariantUpdateRequest;
import com.bakeby.bakebyapi.catalog.entity.Category;
import com.bakeby.bakebyapi.catalog.entity.DietaryType;
import com.bakeby.bakebyapi.catalog.entity.Product;
import com.bakeby.bakebyapi.catalog.entity.ProductDetails;
import com.bakeby.bakebyapi.catalog.entity.Variant;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.repository.VariantRepository;
import com.bakeby.bakebyapi.common.exception.ConflictException;
import com.bakeby.bakebyapi.common.exception.FieldValidationException;
import com.bakeby.bakebyapi.common.exception.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class VariantServiceTests extends AbstractIntegrationTest {

    @Autowired VariantService service;
    @Autowired ProductService productService;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired VariantRepository variants;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    // ---- helpers ----

    private Long product(String slug, boolean active) {
        Category c = categories.saveAndFlush(Category.builder().name("c-" + slug).slug("c-" + slug).build());
        return products.saveAndFlush(Product.builder().category(c).name("P " + slug).slug(slug).active(active)
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build()).build()).getId();
    }

    private static VariantCreateRequest createReq(String sku, Long price, Long compareAt) {
        return new VariantCreateRequest(sku, "500 g", price, compareAt, null, null);
    }

    private static VariantUpdateRequest updateReq(String sku, Long price, Long compareAt, boolean active) {
        return new VariantUpdateRequest(sku, "1 kg", price, compareAt, false, active);
    }

    private static void assertFieldError(Throwable t, String field) {
        assertThat(t).isInstanceOf(FieldValidationException.class);
        assertThat(((FieldValidationException) t).getErrors()).containsOnlyKeys(field);
    }

    // ---- create ----

    @Test
    void createPersistsVariantWithConfiguredCurrencyAndDefaults() {
        Long p = product("cake", true);
        VariantResponse r = service.create(p, createReq("CTC-500G", 65000L, 75000L));

        assertThat(r.id()).isNotNull();
        assertThat(r.sku()).isEqualTo("CTC-500G");
        assertThat(r.label()).isEqualTo("500 g");
        assertThat(r.priceMinor()).isEqualTo(65000L);
        assertThat(r.compareAtPriceMinor()).isEqualTo(75000L);
        assertThat(r.currency()).isEqualTo("INR");
        assertThat(r.inStock()).isTrue();
        assertThat(r.active()).isTrue();
        Variant saved = variants.findById(r.id()).orElseThrow();
        assertThat(saved.getProduct().getId()).isEqualTo(p);
    }

    @Test
    void createAllowsZeroPriceAndNoCompareAtPrice() {
        Long p = product("cake", true);
        VariantResponse r = service.create(p, createReq("FREE", 0L, null));
        assertThat(r.priceMinor()).isZero();
        assertThat(r.compareAtPriceMinor()).isNull();
    }

    @Test
    void createTrimsLabel() {
        Long p = product("cake", true);
        VariantResponse r = service.create(p,
                new VariantCreateRequest("SKU-1", "  500 g ", 100L, null, false, true));
        assertThat(r.label()).isEqualTo("500 g");
        assertThat(r.inStock()).isFalse();
    }

    @Test
    void createThrowsConflictWhenSkuExistsUnderSameProduct() {
        Long p = product("cake", true);
        service.create(p, createReq("DUP", 100L, null));
        assertThatThrownBy(() -> service.create(p, createReq("DUP", 200L, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(variants.count()).isEqualTo(1);
    }

    @Test
    void createThrowsConflictWhenSkuExistsUnderAnotherProduct() {
        Long a = product("a", true);
        Long b = product("b", true);
        service.create(a, createReq("DUP", 100L, null));
        assertThatThrownBy(() -> service.create(b, createReq("DUP", 100L, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createThrowsConflictWhenSkuBelongsToInactiveVariant() {
        Long p = product("cake", true);
        VariantResponse v = service.create(p, createReq("OLD", 100L, null));
        service.delete(p, v.id());
        assertThatThrownBy(() -> service.create(p, createReq("OLD", 100L, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRejectsNegativePrice() {
        Long p = product("cake", true);
        Throwable t = catchThrowable(
                () -> service.create(p, createReq("NEG", -1L, null)));
        assertFieldError(t, "priceMinor");
    }

    @Test
    void createRejectsCompareAtEqualToPrice() {
        Long p = product("cake", true);
        Throwable t = catchThrowable(
                () -> service.create(p, createReq("EQ", 1000L, 1000L)));
        assertFieldError(t, "compareAtPriceMinor");
    }

    @Test
    void createRejectsCompareAtBelowPrice() {
        Long p = product("cake", true);
        Throwable t = catchThrowable(
                () -> service.create(p, createReq("LOW", 1000L, 999L)));
        assertFieldError(t, "compareAtPriceMinor");
        assertThat(variants.count()).isZero();
    }

    @Test
    void createRejectsSkuWithInvalidCharacters() {
        Long p = product("cake", true);
        Throwable t = catchThrowable(
                () -> service.create(p, createReq("BAD SKU!", 100L, null)));
        assertFieldError(t, "sku");
    }

    @Test
    void createRejectsBlankSku() {
        Long p = product("cake", true);
        Throwable t = catchThrowable(
                () -> service.create(p, createReq("", 100L, null)));
        assertFieldError(t, "sku");
    }

    @Test
    void createRejectsLabelOfOnlyNonBreakingSpaces() {
        Long p = product("cake", true);
        Throwable t = catchThrowable(
                () -> service.create(p, new VariantCreateRequest("SKU", "  ", 100L, null, null, null)));
        assertFieldError(t, "label");
    }

    @Test
    void createThrowsNotFoundForUnknownProduct() {
        assertThatThrownBy(() -> service.create(999_999L, createReq("X", 100L, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createThrowsConflictForActiveVariantUnderInactiveProduct() {
        Long p = product("cake", false);
        assertThatThrownBy(() -> service.create(p, createReq("X", 100L, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(variants.count()).isZero();
    }

    @Test
    void createAllowsInactiveVariantUnderInactiveProduct() {
        Long p = product("cake", false);
        VariantResponse r = service.create(p, new VariantCreateRequest("X", "500 g", 100L, null, null, false));
        assertThat(r.active()).isFalse();
    }

    // ---- list / get ----

    @Test
    void listReturnsVariantsOrderedByIdAndFiltersInactiveWhenAsked() {
        Long p = product("cake", true);
        VariantResponse a = service.create(p, createReq("A", 100L, null));
        VariantResponse b = service.create(p, createReq("B", 100L, null));
        VariantResponse c = service.create(p, createReq("C", 100L, null));
        service.delete(p, b.id());

        assertThat(service.list(p, true)).extracting(VariantResponse::id).containsExactly(a.id(), b.id(), c.id());
        assertThat(service.list(p, false)).extracting(VariantResponse::id).containsExactly(a.id(), c.id());
    }

    @Test
    void listDoesNotIncludeOtherProductsVariants() {
        Long a = product("a", true);
        Long b = product("b", true);
        service.create(a, createReq("A1", 100L, null));
        service.create(b, createReq("B1", 100L, null));
        assertThat(service.list(a, true)).extracting(VariantResponse::sku).containsExactly("A1");
    }

    @Test
    void listThrowsNotFoundForUnknownProduct() {
        assertThatThrownBy(() -> service.list(999_999L, true)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getReturnsVariantWithCurrency() {
        Long p = product("cake", true);
        VariantResponse v = service.create(p, createReq("G", 100L, 200L));
        VariantResponse got = service.get(p, v.id());
        assertThat(got).isEqualTo(v);
        assertThat(got.currency()).isEqualTo("INR");
    }

    @Test
    void getThrowsNotFoundWhenVariantBelongsToAnotherProduct() {
        Long a = product("a", true);
        Long b = product("b", true);
        VariantResponse v = service.create(a, createReq("A1", 100L, null));
        assertThatThrownBy(() -> service.get(b, v.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getThrowsNotFoundForUnknownVariantOrProduct() {
        Long p = product("cake", true);
        assertThatThrownBy(() -> service.get(p, 999_999L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(999_999L, 1L)).isInstanceOf(NotFoundException.class);
    }

    // ---- update ----

    @Test
    void updateReplacesAllFieldsAndClearsOmittedCompareAt() {
        Long p = product("cake", true);
        VariantResponse v = service.create(p, createReq("OLD", 100L, 200L));
        VariantResponse r = service.update(p, v.id(), updateReq("NEW", 500L, null, true));

        assertThat(r.id()).isEqualTo(v.id());
        assertThat(r.sku()).isEqualTo("NEW");
        assertThat(r.label()).isEqualTo("1 kg");
        assertThat(r.priceMinor()).isEqualTo(500L);
        assertThat(r.compareAtPriceMinor()).isNull();
        assertThat(r.inStock()).isFalse();
        assertThat(r.currency()).isEqualTo("INR");
        Variant saved = variants.findById(v.id()).orElseThrow();
        assertThat(saved.getSku()).isEqualTo("NEW");
        assertThat(saved.getCompareAtPriceMinor()).isNull();
        assertThat(saved.getProduct().getId()).isEqualTo(p);
    }

    @Test
    void updateKeepingOwnSkuIsAllowed() {
        Long p = product("cake", true);
        VariantResponse v = service.create(p, createReq("SAME", 100L, null));
        assertThatCode(() -> service.update(p, v.id(), updateReq("SAME", 300L, 400L, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void updateThrowsConflictWhenSkuTakenByAnotherVariant() {
        Long p = product("cake", true);
        service.create(p, createReq("TAKEN", 100L, null));
        VariantResponse v = service.create(p, createReq("MINE", 100L, null));
        assertThatThrownBy(() -> service.update(p, v.id(), updateReq("TAKEN", 100L, null, true)))
                .isInstanceOf(ConflictException.class);
        assertThat(variants.findById(v.id()).orElseThrow().getSku()).isEqualTo("MINE");
    }

    @Test
    void updateRejectsCompareAtNotGreaterThanPrice() {
        Long p = product("cake", true);
        VariantResponse v = service.create(p, createReq("V", 100L, null));
        Throwable t = catchThrowable(
                () -> service.update(p, v.id(), updateReq("V", 500L, 500L, true)));
        assertFieldError(t, "compareAtPriceMinor");
    }

    @Test
    void updateRejectsNegativePrice() {
        Long p = product("cake", true);
        VariantResponse v = service.create(p, createReq("V", 100L, null));
        Throwable t = catchThrowable(
                () -> service.update(p, v.id(), updateReq("V", -5L, null, true)));
        assertFieldError(t, "priceMinor");
    }

    @Test
    void updateThrowsNotFoundWhenVariantBelongsToAnotherProduct() {
        Long a = product("a", true);
        Long b = product("b", true);
        VariantResponse v = service.create(a, createReq("A1", 100L, null));
        assertThatThrownBy(() -> service.update(b, v.id(), updateReq("A1", 100L, null, true)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateThrowsConflictWhenActivatingVariantUnderInactiveProduct() {
        Long p = product("cake", true);
        VariantResponse v = service.create(p, createReq("V", 100L, null));
        productService.delete(p);
        assertThat(variants.findById(v.id()).orElseThrow().isActive()).isFalse();

        assertThatThrownBy(() -> service.update(p, v.id(), updateReq("V", 100L, null, true)))
                .isInstanceOf(ConflictException.class);
        assertThatCode(() -> service.update(p, v.id(), updateReq("V", 200L, null, false)))
                .doesNotThrowAnyException();
    }

    // ---- delete ----

    @Test
    void deleteSoftDeletesAndIsIdempotent() {
        Long p = product("cake", true);
        VariantResponse v = service.create(p, createReq("D", 100L, null));
        service.delete(p, v.id());
        assertThat(variants.findById(v.id()).orElseThrow().isActive()).isFalse();
        assertThatCode(() -> service.delete(p, v.id())).doesNotThrowAnyException();
        assertThat(variants.findById(v.id())).isPresent();
    }

    @Test
    void deleteThrowsNotFoundWhenVariantBelongsToAnotherProduct() {
        Long a = product("a", true);
        Long b = product("b", true);
        VariantResponse v = service.create(a, createReq("A1", 100L, null));
        assertThatThrownBy(() -> service.delete(b, v.id())).isInstanceOf(NotFoundException.class);
        assertThat(variants.findById(v.id()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void requireValidPricesAcceptsCompareAtOneAbovePrice() {
        assertThatCode(() -> VariantService.requireValidPrices(100L, 101L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> VariantService.requireValidPrices(null, null))
                .isInstanceOf(FieldValidationException.class);
        List.of(0L, Long.MAX_VALUE).forEach(price ->
                assertThatCode(() -> VariantService.requireValidPrices(price, null)).doesNotThrowAnyException());
    }
}

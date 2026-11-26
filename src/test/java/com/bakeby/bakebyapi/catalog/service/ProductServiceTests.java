package com.bakeby.bakebyapi.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.domain.Allergen;
import com.bakeby.bakebyapi.catalog.domain.Category;
import com.bakeby.bakebyapi.catalog.domain.DietaryType;
import com.bakeby.bakebyapi.catalog.domain.Product;
import com.bakeby.bakebyapi.catalog.domain.Variant;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.repository.VariantRepository;
import com.bakeby.bakebyapi.catalog.web.ProductCreateRequest;
import com.bakeby.bakebyapi.catalog.web.ProductDetailsRequest;
import com.bakeby.bakebyapi.catalog.web.ProductResponse;
import com.bakeby.bakebyapi.catalog.web.ProductUpdateRequest;
import com.bakeby.bakebyapi.catalog.web.VariantResponse;
import com.bakeby.bakebyapi.common.BadRequestException;
import com.bakeby.bakebyapi.common.ConflictException;
import com.bakeby.bakebyapi.common.FieldValidationException;
import com.bakeby.bakebyapi.common.NotFoundException;
import com.bakeby.bakebyapi.common.PageResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductServiceTests extends AbstractIntegrationTest {

    @Autowired ProductService service;
    @Autowired CategoryService categoryService;
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

    private Long category(String slug, boolean active) {
        return categories.saveAndFlush(Category.builder().name(slug).slug(slug).active(active).build()).getId();
    }

    private static ProductDetailsRequest details() {
        return new ProductDetailsRequest("desc", "flour", Set.of(Allergen.GLUTEN), DietaryType.VEG, 2, 24, 6);
    }

    private ProductCreateRequest createReq(String name, String slug, Long categoryId) {
        return new ProductCreateRequest(name, slug, categoryId, null, details(), List.of("https://e.com/a.jpg"));
    }

    private ProductResponse create(String name, Long categoryId) {
        return service.create(createReq(name, null, categoryId));
    }

    private ProductUpdateRequest updateReq(String name, String slug, Long categoryId, boolean active) {
        return new ProductUpdateRequest(name, slug, categoryId, active, details(), List.of("https://e.com/a.jpg"));
    }

    private Variant addVariant(Long productId, String sku, boolean active) {
        Product p = products.findById(productId).orElseThrow();
        return variants.saveAndFlush(Variant.builder().product(p).sku(sku).label(sku).priceMinor(1000)
                .active(active).build());
    }

    private boolean variantActive(Long variantId) {
        return variants.findById(variantId).orElseThrow().isActive();
    }

    // ---- create ----

    @Test
    void createsActiveProductWithDetailsImagesAndDerivedSlug() {
        Long cat = category("cakes", true);
        ProductResponse r = service.create(new ProductCreateRequest("Chocolate Truffle Cake", null, cat, null,
                new ProductDetailsRequest("Rich", "Cocoa", Set.of(Allergen.EGGS, Allergen.GLUTEN, Allergen.DAIRY),
                        DietaryType.EGGLESS, 2, 24, 6),
                List.of("https://e.com/1.jpg", "https://e.com/2.jpg")));
        assertThat(r.id()).isNotNull();
        assertThat(r.slug()).isEqualTo("chocolate-truffle-cake");
        assertThat(r.categoryId()).isEqualTo(cat);
        assertThat(r.active()).isTrue();
        assertThat(r.details().description()).isEqualTo("Rich");
        assertThat(r.details().ingredients()).isEqualTo("Cocoa");
        assertThat(r.details().allergens()).containsExactly(Allergen.GLUTEN, Allergen.DAIRY, Allergen.EGGS);
        assertThat(r.details().dietaryType()).isEqualTo(DietaryType.EGGLESS);
        assertThat(r.details().shelfLifeDays()).isEqualTo(2);
        assertThat(r.details().leadTimeHours()).isEqualTo(24);
        assertThat(r.details().serves()).isEqualTo(6);
        assertThat(r.images()).containsExactly("https://e.com/1.jpg", "https://e.com/2.jpg");
        assertThat(r.variants()).isEmpty();
    }

    @Test
    void createPersistsImagesInGivenOrder() {
        Long cat = category("cakes", true);
        ProductResponse r = service.create(new ProductCreateRequest("Cake", null, cat, null, details(),
                List.of("https://e.com/z.jpg", "https://e.com/a.jpg", "https://e.com/m.jpg")));
        assertThat(service.get(r.id()).images())
                .containsExactly("https://e.com/z.jpg", "https://e.com/a.jpg", "https://e.com/m.jpg");
    }

    @Test
    void createWithNullImagesAndAllergensStoresNone() {
        Long cat = category("cakes", true);
        ProductResponse r = service.create(new ProductCreateRequest("Cake", null, cat, null,
                new ProductDetailsRequest(null, null, null, DietaryType.VEG, null, null, null), null));
        ProductResponse fetched = service.get(r.id());
        assertThat(fetched.images()).isEmpty();
        assertThat(fetched.details().allergens()).isEmpty();
    }

    @Test
    void createsInactiveProductWhenActiveFalseGiven() {
        Long cat = category("cakes", true);
        ProductResponse r = service.create(new ProductCreateRequest("Cake", null, cat, false, details(), null));
        assertThat(r.active()).isFalse();
    }

    @Test
    void appendsDashTwoThenDashThreeWhenDerivedSlugCollides() {
        Long cat = category("cakes", true);
        assertThat(create("Plum Cake", cat).slug()).isEqualTo("plum-cake");
        assertThat(create("Plum Cake", cat).slug()).isEqualTo("plum-cake-2");
        assertThat(create("Plum  Cake!", cat).slug()).isEqualTo("plum-cake-3");
    }

    @Test
    void suffixingPicksLowestFreeSuffix() {
        Long cat = category("cakes", true);
        service.create(createReq("Plum Cake", null, cat));
        service.create(createReq("Other", "plum-cake-3", cat));
        assertThat(create("Plum Cake", cat).slug()).isEqualTo("plum-cake-2");
        assertThat(create("Plum Cake", cat).slug()).isEqualTo("plum-cake-4");
    }

    @Test
    void suffixingIgnoresSlugsThatOnlySharePrefix() {
        Long cat = category("cakes", true);
        service.create(createReq("Pop", "cake-pop", cat));
        assertThat(create("Cake", cat).slug()).isEqualTo("cake");
    }

    @Test
    void suffixingCountsInactiveProductsAsTaken() {
        Long cat = category("cakes", true);
        ProductResponse first = create("Plum Cake", cat);
        service.delete(first.id());
        assertThat(create("Plum Cake", cat).slug()).isEqualTo("plum-cake-2");
    }

    @Test
    void usesExplicitSlugWhenProvided() {
        Long cat = category("cakes", true);
        assertThat(service.create(createReq("Cake", "my-cake", cat)).slug()).isEqualTo("my-cake");
    }

    @Test
    void throwsConflictWhenExplicitSlugExists() {
        Long cat = category("cakes", true);
        service.create(createReq("Cake", "same", cat));
        assertThatThrownBy(() -> service.create(createReq("Other", "same", cat)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsAllDigitExplicitSlug() {
        Long cat = category("cakes", true);
        assertThatThrownBy(() -> service.create(createReq("Cake", "123", cat)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Invalid slug");
    }

    @Test
    void rejectsNameThatDerivesAllDigitOrEmptySlug() {
        Long cat = category("cakes", true);
        assertThatThrownBy(() -> create("2024", cat))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot derive a slug from the name; supply a slug");
        assertThatThrownBy(() -> create("!!!", cat))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot derive a slug from the name; supply a slug");
    }

    @Test
    void createTrimsNameIncludingNonBreakingSpaces() {
        Long cat = category("cakes", true);
        ProductResponse r = create("  Plum Cake  ", cat);
        assertThat(r.name()).isEqualTo("Plum Cake");
        assertThat(r.slug()).isEqualTo("plum-cake");
    }

    @Test
    void createRejectsNameOfOnlyNonBreakingSpacesAsNameFieldError() {
        Long cat = category("cakes", true);
        assertThatThrownBy(() -> create("  ", cat))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getErrors()).containsOnlyKeys("name"));
    }

    @Test
    void createThrowsFieldErrorOnCategoryIdWhenCategoryUnknown() {
        assertThatThrownBy(() -> create("Cake", 999999L))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getErrors()).containsOnlyKeys("categoryId"));
        assertThat(products.count()).isZero();
    }

    @Test
    void createThrowsConflictForActiveProductInInactiveCategory() {
        Long cat = category("hidden", false);
        assertThatThrownBy(() -> create("Cake", cat)).isInstanceOf(ConflictException.class);
        assertThat(products.count()).isZero();
    }

    @Test
    void createAllowsInactiveProductInInactiveCategory() {
        Long cat = category("hidden", false);
        ProductResponse r = service.create(new ProductCreateRequest("Cake", null, cat, false, details(), null));
        assertThat(r.active()).isFalse();
    }

    // ---- get / list ----

    @Test
    void getThrowsNotFoundForMissingId() {
        assertThatThrownBy(() -> service.get(999999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getEmbedsAllVariantsIncludingInactiveWithCurrency() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        Variant a = addVariant(p.id(), "CAKE-500G", true);
        Variant b = addVariant(p.id(), "CAKE-1KG", false);
        ProductResponse r = service.get(p.id());
        assertThat(r.variants()).extracting(VariantResponse::id).containsExactly(a.getId(), b.getId());
        assertThat(r.variants()).extracting(VariantResponse::active).containsExactly(true, false);
        assertThat(r.variants()).extracting(VariantResponse::currency).containsOnly("INR");
        assertThat(r.variants().getFirst().priceMinor()).isEqualTo(1000);
    }

    @Test
    void listIncludesInactiveWhenRequestedAndHidesThemOtherwise() {
        Long cat = category("cakes", true);
        create("Apple", cat);
        ProductResponse hidden = create("Banana", cat);
        service.delete(hidden.id());
        PageResponse<ProductResponse> all = service.list(true, PageRequest.of(0, 10, Sort.by("name")));
        assertThat(all.content()).extracting(ProductResponse::name).containsExactly("Apple", "Banana");
        assertThat(all.totalElements()).isEqualTo(2);
        PageResponse<ProductResponse> active = service.list(false, PageRequest.of(0, 10, Sort.by("name")));
        assertThat(active.content()).extracting(ProductResponse::name).containsExactly("Apple");
        assertThat(active.totalElements()).isEqualTo(1);
    }

    @Test
    void listAttachesEachProductsOwnVariantsImagesAndAllergens() {
        Long cat = category("cakes", true);
        ProductResponse a = service.create(new ProductCreateRequest("Apple", null, cat, null,
                new ProductDetailsRequest(null, null, Set.of(Allergen.NUTS), DietaryType.VEG, null, null, null),
                List.of("https://e.com/a.jpg")));
        ProductResponse b = service.create(new ProductCreateRequest("Banana", null, cat, null,
                new ProductDetailsRequest(null, null, Set.of(Allergen.SOY), DietaryType.VEGAN, null, null, null),
                List.of("https://e.com/b1.jpg", "https://e.com/b2.jpg")));
        addVariant(a.id(), "A-1", true);
        addVariant(b.id(), "B-1", true);
        addVariant(b.id(), "B-2", false);
        List<ProductResponse> list = service.list(true, PageRequest.of(0, 10, Sort.by("name"))).content();
        assertThat(list.get(0).variants()).extracting(VariantResponse::sku).containsExactly("A-1");
        assertThat(list.get(0).images()).containsExactly("https://e.com/a.jpg");
        assertThat(list.get(0).details().allergens()).containsExactly(Allergen.NUTS);
        assertThat(list.get(1).variants()).extracting(VariantResponse::sku).containsExactly("B-1", "B-2");
        assertThat(list.get(1).images()).containsExactly("https://e.com/b1.jpg", "https://e.com/b2.jpg");
        assertThat(list.get(1).details().allergens()).containsExactly(Allergen.SOY);
    }

    @Test
    void listSecondPageReportsZeroBasedPageAndTotals() {
        Long cat = category("cakes", true);
        for (String n : new String[] {"A", "B", "C", "D", "E"}) {
            create(n, cat);
        }
        PageResponse<ProductResponse> r = service.list(true, PageRequest.of(2, 2, Sort.by("name")));
        assertThat(r.content()).extracting(ProductResponse::name).containsExactly("E");
        assertThat(r.page()).isEqualTo(2);
        assertThat(r.size()).isEqualTo(2);
        assertThat(r.totalElements()).isEqualTo(5);
        assertThat(r.totalPages()).isEqualTo(3);
    }

    // ---- update ----

    @Test
    void updateReplacesAllWritableFields() {
        Long cat = category("cakes", true);
        Long other = category("pies", true);
        ProductResponse p = create("Cake", cat);
        ProductResponse u = service.update(p.id(), new ProductUpdateRequest("Apple Pie", null, other, true,
                new ProductDetailsRequest("New", "Apples", Set.of(Allergen.DAIRY), DietaryType.VEGAN, 3, 12, 8),
                List.of("https://e.com/pie.jpg")));
        assertThat(u.name()).isEqualTo("Apple Pie");
        assertThat(u.categoryId()).isEqualTo(other);
        assertThat(u.details().description()).isEqualTo("New");
        assertThat(u.details().allergens()).containsExactly(Allergen.DAIRY);
        assertThat(u.details().dietaryType()).isEqualTo(DietaryType.VEGAN);
        assertThat(u.details().serves()).isEqualTo(8);
        assertThat(u.images()).containsExactly("https://e.com/pie.jpg");
        assertThat(service.get(p.id())).isEqualTo(u);
    }

    @Test
    void updateWithNullImagesAllergensAndOptionalDetailsClearsThem() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        service.update(p.id(), new ProductUpdateRequest("Cake", null, cat, true,
                new ProductDetailsRequest(null, null, null, DietaryType.VEG, null, null, null), null));
        ProductResponse r = service.get(p.id());
        assertThat(r.images()).isEmpty();
        assertThat(r.details().allergens()).isEmpty();
        assertThat(r.details().description()).isNull();
        assertThat(r.details().shelfLifeDays()).isNull();
        assertThat(r.details().serves()).isNull();
    }

    @Test
    void updateReordersImages() {
        Long cat = category("cakes", true);
        ProductResponse p = service.create(new ProductCreateRequest("Cake", null, cat, null, details(),
                List.of("https://e.com/1.jpg", "https://e.com/2.jpg", "https://e.com/3.jpg")));
        service.update(p.id(), new ProductUpdateRequest("Cake", null, cat, true, details(),
                List.of("https://e.com/3.jpg", "https://e.com/1.jpg")));
        assertThat(service.get(p.id()).images()).containsExactly("https://e.com/3.jpg", "https://e.com/1.jpg");
    }

    @Test
    void updateWithoutSlugKeepsSlugEvenWhenNameChanges() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        assertThat(service.update(p.id(), updateReq("Totally New Name", null, cat, true)).slug()).isEqualTo("cake");
    }

    @Test
    void updateChangesSlugWhenSuppliedAndFree() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        assertThat(service.update(p.id(), updateReq("Cake", "new-slug", cat, true)).slug()).isEqualTo("new-slug");
    }

    @Test
    void updateKeepingOwnSlugIsNotAConflict() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        assertThat(service.update(p.id(), updateReq("Cake", "cake", cat, true)).slug()).isEqualTo("cake");
    }

    @Test
    void updateThrowsConflictWhenSlugBelongsToAnotherProduct() {
        Long cat = category("cakes", true);
        create("Cake", cat);
        ProductResponse pie = create("Pie", cat);
        assertThatThrownBy(() -> service.update(pie.id(), updateReq("Pie", "cake", cat, true)))
                .isInstanceOf(ConflictException.class);
        assertThat(service.get(pie.id()).slug()).isEqualTo("pie");
    }

    @Test
    void updateRejectsAllDigitSlug() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        assertThatThrownBy(() -> service.update(p.id(), updateReq("Cake", "42", cat, true)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateThrowsNotFoundForMissingId() {
        Long cat = category("cakes", true);
        assertThatThrownBy(() -> service.update(999999L, updateReq("X", null, cat, true)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateThrowsFieldErrorOnCategoryIdWhenCategoryUnknown() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        assertThatThrownBy(() -> service.update(p.id(), updateReq("Cake", null, 999999L, true)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getErrors()).containsOnlyKeys("categoryId"));
        assertThat(service.get(p.id()).categoryId()).isEqualTo(cat);
    }

    @Test
    void updateThrowsConflictWhenMovingActiveProductToInactiveCategory() {
        Long cat = category("cakes", true);
        Long hidden = category("hidden", false);
        ProductResponse p = create("Cake", cat);
        assertThatThrownBy(() -> service.update(p.id(), updateReq("Cake", null, hidden, true)))
                .isInstanceOf(ConflictException.class);
        assertThat(service.get(p.id()).categoryId()).isEqualTo(cat);
    }

    @Test
    void updateAllowsMovingInactiveProductToInactiveCategory() {
        Long cat = category("cakes", true);
        Long hidden = category("hidden", false);
        ProductResponse p = create("Cake", cat);
        ProductResponse u = service.update(p.id(), updateReq("Cake", null, hidden, false));
        assertThat(u.categoryId()).isEqualTo(hidden);
        assertThat(u.active()).isFalse();
    }

    @Test
    void reactivationThrowsConflictWhenCategoryInactive() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        service.delete(p.id());
        categoryService.delete(cat);
        assertThatThrownBy(() -> service.update(p.id(), updateReq("Cake", null, cat, true)))
                .isInstanceOf(ConflictException.class);
        assertThat(service.get(p.id()).active()).isFalse();
    }

    @Test
    void reactivationSucceedsWhenCategoryActiveButDoesNotReactivateVariants() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        Variant v = addVariant(p.id(), "CAKE-1", true);
        service.delete(p.id());
        ProductResponse u = service.update(p.id(), updateReq("Cake", null, cat, true));
        assertThat(u.active()).isTrue();
        assertThat(variantActive(v.getId())).isFalse();
        assertThat(u.variants()).extracting(VariantResponse::active).containsExactly(false);
    }

    @Test
    void updateActiveTrueToFalseDeactivatesVariants() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        Variant v1 = addVariant(p.id(), "CAKE-1", true);
        Variant v2 = addVariant(p.id(), "CAKE-2", true);
        ProductResponse u = service.update(p.id(), updateReq("Cake", null, cat, false));
        assertThat(u.active()).isFalse();
        assertThat(u.variants()).extracting(VariantResponse::active).containsExactly(false, false);
        assertThat(variantActive(v1.getId())).isFalse();
        assertThat(variantActive(v2.getId())).isFalse();
    }

    @Test
    void updateKeepingActiveTrueLeavesVariantsActive() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        Variant v = addVariant(p.id(), "CAKE-1", true);
        service.update(p.id(), updateReq("Cake", null, cat, true));
        assertThat(variantActive(v.getId())).isTrue();
    }

    // ---- delete ----

    @Test
    void deleteSoftDeactivatesProductAndKeepsRow() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        service.delete(p.id());
        assertThat(products.findById(p.id())).isPresent();
        assertThat(service.get(p.id()).active()).isFalse();
    }

    @Test
    void deleteCascadesDeactivationToAllVariants() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        Variant v1 = addVariant(p.id(), "CAKE-1", true);
        Variant v2 = addVariant(p.id(), "CAKE-2", true);
        service.delete(p.id());
        assertThat(variantActive(v1.getId())).isFalse();
        assertThat(variantActive(v2.getId())).isFalse();
    }

    @Test
    void deleteDoesNotTouchOtherProductsVariants() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        ProductResponse other = create("Pie", cat);
        addVariant(p.id(), "CAKE-1", true);
        Variant keep = addVariant(other.id(), "PIE-1", true);
        service.delete(p.id());
        assertThat(variantActive(keep.getId())).isTrue();
    }

    @Test
    void deleteIsIdempotentForAlreadyInactiveProduct() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        service.delete(p.id());
        service.delete(p.id());
        assertThat(service.get(p.id()).active()).isFalse();
    }

    @Test
    void deleteOfInactiveProductStillDeactivatesStrayActiveVariants() {
        Long cat = category("cakes", true);
        ProductResponse p = service.create(new ProductCreateRequest("Cake", null, cat, false, details(), null));
        Variant v = addVariant(p.id(), "CAKE-1", true);
        service.delete(p.id());
        assertThat(variantActive(v.getId())).isFalse();
    }

    @Test
    void deleteThrowsNotFoundForMissingId() {
        assertThatThrownBy(() -> service.delete(999999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingLastActiveProductAllowsCategoryDeletion() {
        Long cat = category("cakes", true);
        ProductResponse p = create("Cake", cat);
        assertThatThrownBy(() -> categoryService.delete(cat)).isInstanceOf(ConflictException.class);
        service.delete(p.id());
        categoryService.delete(cat);
        assertThat(categories.findById(cat).orElseThrow().isActive()).isFalse();
    }
}

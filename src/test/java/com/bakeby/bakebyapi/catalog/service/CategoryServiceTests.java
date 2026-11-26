package com.bakeby.bakebyapi.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.domain.Category;
import com.bakeby.bakebyapi.catalog.domain.DietaryType;
import com.bakeby.bakebyapi.catalog.domain.Product;
import com.bakeby.bakebyapi.catalog.domain.ProductDetails;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.web.CategoryCreateRequest;
import com.bakeby.bakebyapi.catalog.web.CategoryResponse;
import com.bakeby.bakebyapi.catalog.web.CategoryUpdateRequest;
import com.bakeby.bakebyapi.common.BadRequestException;
import com.bakeby.bakebyapi.common.ConflictException;
import com.bakeby.bakebyapi.common.NotFoundException;
import com.bakeby.bakebyapi.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CategoryServiceTests extends AbstractIntegrationTest {

    @Autowired CategoryService service;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    private CategoryResponse create(String name) {
        return service.create(new CategoryCreateRequest(name, null, null, null));
    }

    private void addProduct(Long categoryId, boolean active) {
        Category cat = categories.findById(categoryId).orElseThrow();
        products.saveAndFlush(Product.builder().category(cat).name("P").slug("p").active(active)
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build()).build());
    }

    @Test
    void listWithIncludeInactiveReturnsAllInPageResponse() {
        create("Active One");
        CategoryResponse hidden = create("Inactive One");
        categories.findById(hidden.id()).ifPresent(c -> {
            c.setActive(false);
            categories.saveAndFlush(c);
        });
        PageResponse<CategoryResponse> r = service.list(true, PageRequest.of(0, 10, Sort.by("name")));
        assertThat(r.content()).extracting(CategoryResponse::name).containsExactly("Active One", "Inactive One");
        assertThat(r.page()).isZero();
        assertThat(r.size()).isEqualTo(10);
        assertThat(r.totalElements()).isEqualTo(2);
        assertThat(r.totalPages()).isEqualTo(1);
    }

    @Test
    void listWithoutIncludeInactiveHidesInactive() {
        create("Active One");
        CategoryResponse hidden = create("Inactive One");
        categories.findById(hidden.id()).ifPresent(c -> {
            c.setActive(false);
            categories.saveAndFlush(c);
        });
        PageResponse<CategoryResponse> r = service.list(false, PageRequest.of(0, 10, Sort.by("name")));
        assertThat(r.content()).extracting(CategoryResponse::name).containsExactly("Active One");
        assertThat(r.totalElements()).isEqualTo(1);
    }

    @Test
    void listSecondPageReportsZeroBasedPageAndTotals() {
        for (String n : new String[] {"A", "B", "C", "D", "E"}) {
            create(n);
        }
        PageResponse<CategoryResponse> r = service.list(true, PageRequest.of(2, 2, Sort.by("name")));
        assertThat(r.content()).extracting(CategoryResponse::name).containsExactly("E");
        assertThat(r.page()).isEqualTo(2);
        assertThat(r.size()).isEqualTo(2);
        assertThat(r.totalElements()).isEqualTo(5);
        assertThat(r.totalPages()).isEqualTo(3);
    }

    @Test
    void createsCategoryActiveByDefaultWithGeneratedSlug() {
        CategoryResponse r = service.create(new CategoryCreateRequest("Birthday Cakes!", null, "desc", null));
        assertThat(r.id()).isNotNull();
        assertThat(r.slug()).isEqualTo("birthday-cakes");
        assertThat(r.active()).isTrue();
        assertThat(r.description()).isEqualTo("desc");
    }

    @Test
    void createsInactiveCategoryWhenActiveFalseGiven() {
        CategoryResponse r = service.create(new CategoryCreateRequest("Hidden", null, null, false));
        assertThat(r.active()).isFalse();
    }

    @Test
    void usesExplicitSlugWhenProvided() {
        CategoryResponse r = service.create(new CategoryCreateRequest("Cakes", "my-cakes", null, null));
        assertThat(r.slug()).isEqualTo("my-cakes");
    }

    @Test
    void throwsConflictWhenExplicitSlugExists() {
        service.create(new CategoryCreateRequest("Cakes", "same", null, null));
        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("Pies", "same", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void throwsConflictWhenGeneratedSlugCollides() {
        service.create(new CategoryCreateRequest("Pies", "pies", null, null));
        assertThatThrownBy(() -> create("Pies!")).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsAllDigitExplicitSlug() {
        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("Numbers", "123", null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsGeneratedAllDigitSlug() {
        assertThatThrownBy(() -> create("2024")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsNameThatSlugifiesToEmpty() {
        assertThatThrownBy(() -> create("!!!")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void throwsConflictWhenNameExistsIgnoringCase() {
        create("Cakes");
        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("CAKES", "other", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateKeepsOwnNameWithoutConflict() {
        CategoryResponse c = create("Cakes");
        CategoryResponse u = service.update(c.id(), new CategoryUpdateRequest("Cakes", null, "new", true));
        assertThat(u.description()).isEqualTo("new");
        assertThat(u.slug()).isEqualTo("cakes");
    }

    @Test
    void updateAllowsChangingOwnNameCasing() {
        CategoryResponse c = create("Cakes");
        assertThat(service.update(c.id(), new CategoryUpdateRequest("CAKES", null, null, true)).name())
                .isEqualTo("CAKES");
    }

    @Test
    void updateThrowsConflictWhenNameBelongsToAnotherCategoryIgnoringCase() {
        create("Cakes");
        CategoryResponse pies = create("Pies");
        assertThatThrownBy(() -> service.update(pies.id(), new CategoryUpdateRequest("cakes", null, null, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateThrowsConflictWhenSlugBelongsToAnotherCategory() {
        create("Cakes");
        CategoryResponse pies = create("Pies");
        assertThatThrownBy(() -> service.update(pies.id(), new CategoryUpdateRequest("Pies", "cakes", null, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateRejectsAllDigitSlug() {
        CategoryResponse c = create("Cakes");
        assertThatThrownBy(() -> service.update(c.id(), new CategoryUpdateRequest("Cakes", "42", null, true)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateChangesSlugWhenValidAndFree() {
        CategoryResponse c = create("Cakes");
        assertThat(service.update(c.id(), new CategoryUpdateRequest("Cakes", "new-slug", null, true)).slug())
                .isEqualTo("new-slug");
    }

    @Test
    void updateThrowsNotFoundForMissingId() {
        assertThatThrownBy(() -> service.update(999999L, new CategoryUpdateRequest("X", null, null, true)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void putActiveTrueReactivatesInactiveCategory() {
        CategoryResponse c = create("Cakes");
        service.delete(c.id());
        assertThat(service.get(c.id()).active()).isFalse();
        assertThat(service.update(c.id(), new CategoryUpdateRequest("Cakes", null, null, true)).active()).isTrue();
    }

    @Test
    void deleteSoftDeactivatesCategoryAndKeepsRow() {
        CategoryResponse c = create("Cakes");
        service.delete(c.id());
        assertThat(categories.findById(c.id())).isPresent();
        assertThat(categories.findById(c.id()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void deleteThrowsConflictWhenCategoryHasActiveProducts() {
        CategoryResponse c = create("Cakes");
        addProduct(c.id(), true);
        assertThatThrownBy(() -> service.delete(c.id())).isInstanceOf(ConflictException.class);
        assertThat(service.get(c.id()).active()).isTrue();
    }

    @Test
    void deleteSucceedsWhenOnlyInactiveProductsRemain() {
        CategoryResponse c = create("Cakes");
        addProduct(c.id(), false);
        service.delete(c.id());
        assertThat(service.get(c.id()).active()).isFalse();
    }

    @Test
    void deleteIsIdempotentForAlreadyInactiveCategory() {
        CategoryResponse c = create("Cakes");
        service.delete(c.id());
        service.delete(c.id());
        assertThat(service.get(c.id()).active()).isFalse();
    }

    @Test
    void deleteThrowsNotFoundForMissingId() {
        assertThatThrownBy(() -> service.delete(999999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getThrowsNotFoundForMissingId() {
        assertThatThrownBy(() -> service.get(999999L)).isInstanceOf(NotFoundException.class);
    }
}

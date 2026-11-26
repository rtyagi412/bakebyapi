package com.bakeby.bakebyapi.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.entity.Allergen;
import com.bakeby.bakebyapi.catalog.entity.Category;
import com.bakeby.bakebyapi.catalog.entity.DietaryType;
import com.bakeby.bakebyapi.catalog.entity.Product;
import com.bakeby.bakebyapi.catalog.entity.ProductDetails;
import com.bakeby.bakebyapi.catalog.entity.Variant;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class CatalogPersistenceTests extends AbstractIntegrationTest {

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired VariantRepository variants;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    private Category cat(String name, String slug) {
        return categories.saveAndFlush(Category.builder().name(name).slug(slug).build());
    }

    private Product prod(Category c, String slug) {
        return products.saveAndFlush(Product.builder().category(c).name("P " + slug).slug(slug)
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build()).build());
    }

    private Variant variant(Product p, String sku, long price, Long compareAt) {
        return variants.saveAndFlush(Variant.builder().product(p).sku(sku).label("L").priceMinor(price)
                .compareAtPriceMinor(compareAt).build());
    }

    @Test
    void savesAndReloadsCategoryWithDefaultsAndTimestamps() {
        Long id = categories.saveAndFlush(Category.builder().name("Cakes").slug("cakes").description("Yum").build()).getId();
        em.clear();
        Category c = categories.findById(id).orElseThrow();
        assertThat(c.getName()).isEqualTo("Cakes");
        assertThat(c.getSlug()).isEqualTo("cakes");
        assertThat(c.getDescription()).isEqualTo("Yum");
        assertThat(c.isActive()).isTrue();
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getUpdatedAt()).isNotNull();
        assertThat(categories.findBySlug("cakes")).isPresent();
        assertThat(categories.existsByNameIgnoreCase("CAKES")).isTrue();
    }

    @Test
    void savesAndReloadsProductWithDetailsAllergensAndOrderedImages() {
        Category c = cat("Cakes", "cakes");
        List<String> imgs = List.of("http://z/3.jpg", "http://a/1.jpg", "http://m/2.jpg");
        Long id = products.saveAndFlush(Product.builder().category(c).name("Choc").slug("choc")
                .details(ProductDetails.builder().description("d").ingredients("i").dietaryType(DietaryType.EGGLESS)
                        .shelfLifeDays(3).leadTimeHours(24).serves(8).build())
                .allergens(Set.of(Allergen.GLUTEN, Allergen.DAIRY)).images(imgs).build()).getId();
        em.clear();
        tx.executeWithoutResult(s -> {
            Product p = products.findById(id).orElseThrow();
            assertThat(p.getCategory().getId()).isEqualTo(c.getId());
            assertThat(p.getDetails().getDescription()).isEqualTo("d");
            assertThat(p.getDetails().getIngredients()).isEqualTo("i");
            assertThat(p.getDetails().getDietaryType()).isEqualTo(DietaryType.EGGLESS);
            assertThat(p.getDetails().getShelfLifeDays()).isEqualTo(3);
            assertThat(p.getDetails().getLeadTimeHours()).isEqualTo(24);
            assertThat(p.getDetails().getServes()).isEqualTo(8);
            assertThat(p.isActive()).isTrue();
            assertThat(p.getAllergens()).containsExactlyInAnyOrder(Allergen.GLUTEN, Allergen.DAIRY);
            assertThat(p.getImages()).containsExactlyElementsOf(imgs);
        });
        assertThat(jdbc.queryForList("SELECT url FROM product_image WHERE product_id=? ORDER BY position", String.class, id))
                .containsExactlyElementsOf(imgs);
    }

    @Test
    void reordersImagesAndPersistsNewOrder() {
        Category c = cat("Cakes", "cakes");
        Long id = products.saveAndFlush(Product.builder().category(c).name("n").slug("n")
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build())
                .images(List.of("a", "b", "c")).build()).getId();
        em.clear();
        tx.executeWithoutResult(s -> {
            Product p = products.findById(id).orElseThrow();
            p.getImages().clear();
            p.getImages().addAll(List.of("c", "a"));
        });
        em.clear();
        tx.executeWithoutResult(s ->
                assertThat(products.findById(id).orElseThrow().getImages()).containsExactly("c", "a"));
    }

    @Test
    void savesProductWithNullOptionalDetailsAndStoresLongText() {
        Category c = cat("Cakes", "cakes");
        String longText = "x".repeat(5000);
        Long id = products.saveAndFlush(Product.builder().category(c).name("n").slug("n")
                .details(ProductDetails.builder().description(longText).dietaryType(DietaryType.VEGAN).build()).build()).getId();
        em.clear();
        Product p = products.findById(id).orElseThrow();
        assertThat(p.getDetails().getDescription()).hasSize(5000);
        assertThat(p.getDetails().getIngredients()).isNull();
        assertThat(p.getDetails().getServes()).isNull();
    }

    @Test
    void savesAndReloadsVariant() {
        Product p = prod(cat("Cakes", "cakes"), "choc");
        Long id = variant(p, "SKU-1", 50000, 60000L).getId();
        em.clear();
        Variant v = variants.findById(id).orElseThrow();
        assertThat(v.getSku()).isEqualTo("SKU-1");
        assertThat(v.getLabel()).isEqualTo("L");
        assertThat(v.getPriceMinor()).isEqualTo(50000);
        assertThat(v.getCompareAtPriceMinor()).isEqualTo(60000L);
        assertThat(v.isInStock()).isTrue();
        assertThat(v.isActive()).isTrue();
        assertThat(v.getProduct().getId()).isEqualTo(p.getId());
        assertThat(variants.findByProductIdAndSku(p.getId(), "SKU-1")).isPresent();
        assertThat(variants.existsBySku("SKU-1")).isTrue();
        assertThat(variants.findByProductId(p.getId())).hasSize(1);
    }

    @Test
    void acceptsZeroPriceAndNullCompareAt() {
        Product p = prod(cat("Cakes", "cakes"), "choc");
        Variant v = variant(p, "FREE", 0, null);
        assertThat(v.getId()).isNotNull();
    }

    @Test
    void rejectsDuplicateCategoryName() {
        cat("Cakes", "cakes");
        assertThatThrownBy(() -> cat("Cakes", "other")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateCategoryNameIgnoringCase() {
        cat("Cakes", "cakes");
        assertThatThrownBy(() -> cat("CAKES", "other")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void excludesOwnIdWhenCheckingCategoryNameIgnoringCase() {
        Category c = cat("Cakes", "cakes");
        assertThat(categories.existsByNameIgnoreCaseAndIdNot("cakes", c.getId())).isFalse();
        assertThat(categories.existsByNameIgnoreCaseAndIdNot("CAKES", c.getId() + 1)).isTrue();
    }

    @Test
    void rejectsDuplicateCategorySlug() {
        cat("Cakes", "cakes");
        assertThatThrownBy(() -> cat("Other", "cakes")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateProductSlug() {
        Category c = cat("Cakes", "cakes");
        prod(c, "choc");
        assertThatThrownBy(() -> prod(c, "choc")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateVariantSku() {
        Product p = prod(cat("Cakes", "cakes"), "choc");
        variant(p, "S", 100, null);
        assertThatThrownBy(() -> variant(p, "S", 100, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativePrice() {
        Product p = prod(cat("Cakes", "cakes"), "choc");
        assertThatThrownBy(() -> variant(p, "S", -1, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCompareAtEqualToPrice() {
        Product p = prod(cat("Cakes", "cakes"), "choc");
        assertThatThrownBy(() -> variant(p, "S", 100, 100L)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCompareAtBelowPrice() {
        Product p = prod(cat("Cakes", "cakes"), "choc");
        assertThatThrownBy(() -> variant(p, "S", 100, 50L)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsProductWithUnknownCategoryViaForeignKey() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product (category_id,name,slug,dietary_type) VALUES (999999,'n','s','VEG')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsVariantWithUnknownProductViaForeignKey() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO variant (product_id,sku,label,price_minor) VALUES (999999,'s','l',1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsImageAndAllergenForUnknownProductViaForeignKey() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product_image (product_id,position,url) VALUES (999999,0,'u')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product_allergen (product_id,allergen) VALUES (999999,'SOY')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDeletingCategoryThatHasProductsViaForeignKey() {
        Category c = cat("Cakes", "cakes");
        prod(c, "choc");
        assertThatThrownBy(() -> categories.deleteById(c.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidProductNumericChecks() {
        Category c = cat("Cakes", "cakes");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product (category_id,name,slug,dietary_type,serves) VALUES (?,'n','s','VEG',0)", c.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product (category_id,name,slug,dietary_type,shelf_life_days) VALUES (?,'n','s2','VEG',-1)", c.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.bakeby.bakebyapi.catalog.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.entity.Allergen;
import com.bakeby.bakebyapi.catalog.entity.Category;
import com.bakeby.bakebyapi.catalog.entity.DietaryType;
import com.bakeby.bakebyapi.catalog.entity.Product;
import com.bakeby.bakebyapi.catalog.entity.ProductDetails;
import com.bakeby.bakebyapi.catalog.entity.Variant;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.repository.VariantRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Public product read API: GET /api/v1/products and /{idOrSlug}. */
class ProductControllerTests extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/products";
    static final String PROBLEM = "application/problem+json";

    @Autowired MockMvc mvc;
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

    private Category category(String slug, boolean active) {
        return categories.saveAndFlush(Category.builder().name(slug).slug(slug).active(active).build());
    }

    private Product product(Category c, String slug, DietaryType type, boolean active) {
        return products.saveAndFlush(Product.builder().category(c).name("Product " + slug).slug(slug).active(active)
                .details(ProductDetails.builder().description("desc " + slug).ingredients("flour")
                        .dietaryType(type).shelfLifeDays(2).leadTimeHours(24).serves(6).build())
                .allergens(Set.of(Allergen.GLUTEN, Allergen.DAIRY))
                .images(List.of("https://e.com/" + slug + "-1.jpg", "https://e.com/" + slug + "-2.jpg"))
                .build());
    }

    private Product product(Category c, String slug) {
        return product(c, slug, DietaryType.VEG, true);
    }

    private Variant variant(Product p, String sku, boolean active) {
        return variants.saveAndFlush(Variant.builder().product(p).sku(sku).label("500 g").priceMinor(65000)
                .compareAtPriceMinor(75000L).active(active).build());
    }

    private void deactivateCategory(Category c) {
        // Admin DELETE would 409 while it has active products; flip the flag directly to model the state.
        jdbc.update("UPDATE category SET active = false WHERE id = ?", c.getId());
    }

    // ---- list: happy path and pagination ----

    @Test
    void listReturnsActiveProductWithDetailsImagesAndVariants() throws Exception {
        Category c = category("cakes", true);
        Product p = product(c, "truffle");
        Variant v = variant(p, "TRF-500", true);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(p.getId()))
                .andExpect(jsonPath("$.content[0].slug").value("truffle"))
                .andExpect(jsonPath("$.content[0].name").value("Product truffle"))
                .andExpect(jsonPath("$.content[0].categoryId").value(c.getId()))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].details.dietaryType").value("VEG"))
                .andExpect(jsonPath("$.content[0].details.description").value("desc truffle"))
                .andExpect(jsonPath("$.content[0].details.allergens", containsInAnyOrder("GLUTEN", "DAIRY")))
                .andExpect(jsonPath("$.content[0].images[0]").value("https://e.com/truffle-1.jpg"))
                .andExpect(jsonPath("$.content[0].images[1]").value("https://e.com/truffle-2.jpg"))
                .andExpect(jsonPath("$.content[0].variants", hasSize(1)))
                .andExpect(jsonPath("$.content[0].variants[0].id").value(v.getId()))
                .andExpect(jsonPath("$.content[0].variants[0].sku").value("TRF-500"))
                .andExpect(jsonPath("$.content[0].variants[0].priceMinor").value(65000))
                .andExpect(jsonPath("$.content[0].variants[0].compareAtPriceMinor").value(75000))
                .andExpect(jsonPath("$.content[0].variants[0].currency").value("INR"))
                .andExpect(jsonPath("$.content[0].variants[0].active").value(true));
    }

    @Test
    void listWith25ProductsSize10Page2ReturnsLast5WithPageMetadata() throws Exception {
        Category c = category("cakes", true);
        for (int i = 0; i < 25; i++) {
            product(c, String.format("cake-%02d", i));
        }
        // default sort is name,asc => "Product cake-00" .. "Product cake-24"
        mvc.perform(get(BASE).param("size", "10").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].slug").value("cake-20"))
                .andExpect(jsonPath("$.content[4].slug").value("cake-24"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void listPageBeyondLastReturnsEmptyContentWithTotals() throws Exception {
        Category c = category("cakes", true);
        for (int i = 0; i < 3; i++) {
            product(c, "cake-" + i);
        }
        mvc.perform(get(BASE).param("size", "10").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listShowsProductWithNoVariantsWithEmptyVariantsArray() throws Exception {
        product(category("cakes", true), "draft");
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].variants").isArray())
                .andExpect(jsonPath("$.content[0].variants", hasSize(0)));
    }

    @Test
    void listShowsProductWhoseVariantsAreAllInactiveWithEmptyVariantsArray() throws Exception {
        Product p = product(category("cakes", true), "draft");
        variant(p, "OFF-1", false);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].variants", hasSize(0)));
    }

    @Test
    void listHonoursSortBySlugDescending() throws Exception {
        Category c = category("cakes", true);
        product(c, "alpha");
        product(c, "zeta");
        mvc.perform(get(BASE).param("sort", "slug,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("zeta"))
                .andExpect(jsonPath("$.content[1].slug").value("alpha"));
    }

    // ---- list: visibility ----

    @Test
    void listHidesInactiveProducts() throws Exception {
        Category c = category("cakes", true);
        product(c, "visible");
        product(c, "hidden", DietaryType.VEG, false);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("visible"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listHidesProductsInInactiveCategory() throws Exception {
        product(category("cakes", true), "visible");
        Category off = category("breads", true);
        product(off, "hidden");
        deactivateCategory(off);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("visible"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listEmbedsOnlyActiveVariantsOrderedById() throws Exception {
        Product p = product(category("cakes", true), "truffle");
        Variant a = variant(p, "A", true);
        variant(p, "B-OFF", false);
        Variant c = variant(p, "C", true);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].variants", hasSize(2)))
                .andExpect(jsonPath("$.content[0].variants[0].id").value(a.getId()))
                .andExpect(jsonPath("$.content[0].variants[1].id").value(c.getId()))
                .andExpect(jsonPath("$.content[0].variants[*].sku", not(hasItem("B-OFF"))));
    }

    // ---- list: filters ----

    @Test
    void listFiltersByCategoryId() throws Exception {
        Category cakes = category("cakes", true);
        Category breads = category("breads", true);
        product(cakes, "cake-1");
        product(breads, "bread-1");
        mvc.perform(get(BASE).param("category", String.valueOf(cakes.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("cake-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listFiltersByCategorySlug() throws Exception {
        Category cakes = category("cakes", true);
        Category breads = category("breads", true);
        product(cakes, "cake-1");
        product(breads, "bread-1");
        product(breads, "bread-2");
        mvc.perform(get(BASE).param("category", "breads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].slug", containsInAnyOrder("bread-1", "bread-2")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listCategoryFilterStillHidesInactiveProducts() throws Exception {
        Category cakes = category("cakes", true);
        product(cakes, "on");
        product(cakes, "off", DietaryType.VEG, false);
        mvc.perform(get(BASE).param("category", "cakes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("on"));
    }

    @Test
    void listFiltersByDietaryType() throws Exception {
        Category c = category("cakes", true);
        product(c, "veg-1", DietaryType.VEG, true);
        product(c, "vegan-1", DietaryType.VEGAN, true);
        product(c, "eggless-1", DietaryType.EGGLESS, true);
        mvc.perform(get(BASE).param("dietaryType", "VEGAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("vegan-1"))
                .andExpect(jsonPath("$.content[0].details.dietaryType").value("VEGAN"));
    }

    @Test
    void listDietaryTypeFilterWithNoMatchesReturnsEmptyPage() throws Exception {
        product(category("cakes", true), "veg-1", DietaryType.VEG, true);
        mvc.perform(get(BASE).param("dietaryType", "NON_VEG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listCombinesCategoryAndDietaryTypeFilters() throws Exception {
        Category cakes = category("cakes", true);
        Category breads = category("breads", true);
        product(cakes, "cake-veg", DietaryType.VEG, true);
        product(cakes, "cake-eggless", DietaryType.EGGLESS, true);
        product(breads, "bread-eggless", DietaryType.EGGLESS, true);
        product(cakes, "cake-eggless-off", DietaryType.EGGLESS, false);
        mvc.perform(get(BASE).param("category", "cakes").param("dietaryType", "EGGLESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("cake-eggless"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(BASE).param("category", String.valueOf(cakes.getId())).param("dietaryType", "EGGLESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("cake-eggless"));
    }

    @Test
    void listFiltersPaginateOverFilteredSet() throws Exception {
        Category cakes = category("cakes", true);
        Category breads = category("breads", true);
        for (int i = 0; i < 12; i++) {
            product(cakes, String.format("cake-%02d", i));
            product(breads, String.format("bread-%02d", i));
        }
        mvc.perform(get(BASE).param("category", "cakes").param("size", "5").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].slug").value("cake-10"))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    // ---- list: validation and not found ----

    @Test
    void listReturns400ProblemForUnknownDietaryType() throws Exception {
        mvc.perform(get(BASE).param("dietaryType", "CARNIVORE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void listReturns400ProblemForUnknownSortField() throws Exception {
        mvc.perform(get(BASE).param("sort", "priceMinor"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail", containsString("Unknown sort field")));
    }

    @Test
    void listReturns400ProblemForInvalidPageAndSize() throws Exception {
        mvc.perform(get(BASE).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(BASE).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listReturns404ProblemForMissingCategoryId() throws Exception {
        mvc.perform(get(BASE).param("category", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listReturns404ProblemForMissingCategorySlug() throws Exception {
        mvc.perform(get(BASE).param("category", "no-such"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listReturns404ProblemForInactiveCategoryFilter() throws Exception {
        Category off = category("breads", false);
        mvc.perform(get(BASE).param("category", "breads"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(BASE).param("category", String.valueOf(off.getId())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listReturns404ForCategoryIdOverflowingLong() throws Exception {
        mvc.perform(get(BASE).param("category", "99999999999999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // ---- detail ----

    @Test
    void getByIdReturnsProductWithActiveVariantsAndImages() throws Exception {
        Category c = category("cakes", true);
        Product p = product(c, "truffle");
        Variant on = variant(p, "ON", true);
        variant(p, "OFF", false);
        mvc.perform(get(BASE + "/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p.getId()))
                .andExpect(jsonPath("$.slug").value("truffle"))
                .andExpect(jsonPath("$.categoryId").value(c.getId()))
                .andExpect(jsonPath("$.details.ingredients").value("flour"))
                .andExpect(jsonPath("$.details.serves").value(6))
                .andExpect(jsonPath("$.details.allergens", containsInAnyOrder("GLUTEN", "DAIRY")))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].id").value(on.getId()))
                .andExpect(jsonPath("$.variants[0].currency").value("INR"));
    }

    @Test
    void getBySlugReturnsProduct() throws Exception {
        Product p = product(category("cakes", true), "red-velvet");
        mvc.perform(get(BASE + "/red-velvet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p.getId()))
                .andExpect(jsonPath("$.variants", hasSize(0)));
    }

    @Test
    void getReturns404ProblemForInactiveProductByIdAndSlug() throws Exception {
        Product p = product(category("cakes", true), "gone", DietaryType.VEG, false);
        mvc.perform(get(BASE + "/" + p.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get(BASE + "/gone"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getReturns404ProblemForProductInInactiveCategory() throws Exception {
        Category c = category("cakes", true);
        Product p = product(c, "orphan");
        deactivateCategory(c);
        mvc.perform(get(BASE + "/" + p.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(BASE + "/orphan"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getReturns404ProblemForMissingProduct() throws Exception {
        mvc.perform(get(BASE + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(BASE + "/no-such-product"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getReturns404ForIdOverflowingLong() throws Exception {
        mvc.perform(get(BASE + "/99999999999999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void publicProductEndpointIsReadOnly() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // ---- acceptance criteria end to end through admin endpoints ----

    @Test
    void adminCreatedCategoryProductAndVariantAreAllVisiblePublicly() throws Exception {
        mvc.perform(post("/api/v1/admin/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cakes\"}"))
                .andExpect(status().isCreated());
        Long catId = jdbc.queryForObject("SELECT max(id) FROM category", Long.class);
        mvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chocolate Truffle Cake\",\"categoryId\":" + catId
                                + ",\"details\":{\"description\":\"Rich\",\"ingredients\":\"Cocoa\","
                                + "\"allergens\":[\"EGGS\",\"GLUTEN\"],\"dietaryType\":\"VEG\","
                                + "\"shelfLifeDays\":2,\"leadTimeHours\":24,\"serves\":6},"
                                + "\"images\":[\"https://e.com/a.jpg\",\"https://e.com/b.jpg\"]}"))
                .andExpect(status().isCreated());
        Long productId = jdbc.queryForObject("SELECT max(id) FROM product", Long.class);
        mvc.perform(post("/api/v1/admin/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"CTC-500\",\"label\":\"500 g\",\"priceMinor\":65000,"
                                + "\"compareAtPriceMinor\":75000}"))
                .andExpect(status().isCreated());

        mvc.perform(get(BASE + "/chocolate-truffle-cake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.name").value("Chocolate Truffle Cake"))
                .andExpect(jsonPath("$.categoryId").value(catId))
                .andExpect(jsonPath("$.details.description").value("Rich"))
                .andExpect(jsonPath("$.details.ingredients").value("Cocoa"))
                .andExpect(jsonPath("$.details.allergens", containsInAnyOrder("EGGS", "GLUTEN")))
                .andExpect(jsonPath("$.details.dietaryType").value("VEG"))
                .andExpect(jsonPath("$.details.shelfLifeDays").value(2))
                .andExpect(jsonPath("$.details.leadTimeHours").value(24))
                .andExpect(jsonPath("$.details.serves").value(6))
                .andExpect(jsonPath("$.images[0]").value("https://e.com/a.jpg"))
                .andExpect(jsonPath("$.images[1]").value("https://e.com/b.jpg"))
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].sku").value("CTC-500"))
                .andExpect(jsonPath("$.variants[0].priceMinor").value(65000))
                .andExpect(jsonPath("$.variants[0].compareAtPriceMinor").value(75000))
                .andExpect(jsonPath("$.variants[0].currency").value("INR"));
        mvc.perform(get("/api/v1/categories/cakes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(catId));
    }

    @Test
    void softDeletedProductIsHiddenPubliclyButStillVisibleToAdmin() throws Exception {
        Product p = product(category("cakes", true), "truffle");
        variant(p, "TRF-1", true);
        mvc.perform(delete("/api/v1/admin/products/" + p.getId())).andExpect(status().isNoContent());

        mvc.perform(get(BASE + "/" + p.getId())).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/truffle")).andExpect(status().isNotFound());
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/products/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminDetailStillShowsInactiveVariantsWhilePublicHidesThem() throws Exception {
        Product p = product(category("cakes", true), "truffle");
        variant(p, "ON", true);
        variant(p, "OFF", false);
        mvc.perform(get("/api/v1/admin/products/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants", hasSize(2)));
        mvc.perform(get(BASE + "/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants", hasSize(1)));
    }
}

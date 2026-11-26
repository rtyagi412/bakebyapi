package com.bakeby.bakebyapi.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.entity.Category;
import com.bakeby.bakebyapi.catalog.entity.DietaryType;
import com.bakeby.bakebyapi.catalog.entity.Product;
import com.bakeby.bakebyapi.catalog.entity.ProductDetails;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.repository.VariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AdminVariantControllerTests extends AbstractIntegrationTest {

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

    private Long product(String slug, boolean active) {
        Category c = categories.saveAndFlush(Category.builder().name("c-" + slug).slug("c-" + slug).build());
        return products.saveAndFlush(Product.builder().category(c).name("P " + slug).slug(slug).active(active)
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build()).build()).getId();
    }

    private static String base(Object productId) {
        return "/api/v1/admin/products/" + productId + "/variants";
    }

    private static String body(String sku, long price, Long compareAt) {
        return "{\"sku\":\"" + sku + "\",\"label\":\"500 g\",\"priceMinor\":" + price
                + (compareAt == null ? "" : ",\"compareAtPriceMinor\":" + compareAt) + "}";
    }

    private static String updateBody(String sku, long price, Long compareAt, boolean inStock, boolean active) {
        return "{\"sku\":\"" + sku + "\",\"label\":\"1 kg\",\"priceMinor\":" + price
                + (compareAt == null ? "" : ",\"compareAtPriceMinor\":" + compareAt)
                + ",\"inStock\":" + inStock + ",\"active\":" + active + "}";
    }

    private ResultActions postJson(Object productId, String body) throws Exception {
        return mvc.perform(post(base(productId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putJson(Object productId, Object variantId, String body) throws Exception {
        return mvc.perform(put(base(productId) + "/" + variantId).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long create(Long productId, String sku) throws Exception {
        postJson(productId, body(sku, 65000, 75000L)).andExpect(status().isCreated());
        return variants.findAll().stream().filter(v -> v.getSku().equals(sku)).findFirst().orElseThrow().getId();
    }

    private static void problem(ResultActions r) throws Exception {
        r.andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("trace"))));
    }

    // ---- POST ----

    @Test
    void createReturns201WithLocationAndBodyIncludingInrCurrency() throws Exception {
        Long p = product("cake", true);
        ResultActions r = postJson(p, body("CTC-500G", 65000, 75000L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sku").value("CTC-500G"))
                .andExpect(jsonPath("$.label").value("500 g"))
                .andExpect(jsonPath("$.priceMinor").value(65000))
                .andExpect(jsonPath("$.compareAtPriceMinor").value(75000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.active").value(true));
        long id = variants.findAll().getFirst().getId();
        r.andExpect(header().string("Location", endsWith(base(p) + "/" + id)));
    }

    @Test
    void createdVariantAppearsInAdminProductResponse() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "CTC-500G");
        mvc.perform(get("/api/v1/admin/products/" + p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].id").value(id))
                .andExpect(jsonPath("$.variants[0].sku").value("CTC-500G"))
                .andExpect(jsonPath("$.variants[0].priceMinor").value(65000))
                .andExpect(jsonPath("$.variants[0].compareAtPriceMinor").value(75000))
                .andExpect(jsonPath("$.variants[0].currency").value("INR"));
    }

    @Test
    void createReturns400WithErrorsMapForBlankSkuBlankLabelAndNegativePrice() throws Exception {
        Long p = product("cake", true);
        ResultActions r = postJson(p, "{\"sku\":\"\",\"label\":\"  \",\"priceMinor\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.label").exists())
                .andExpect(jsonPath("$.errors.priceMinor").exists());
        problem(r);
        assertThat(variants.count()).isZero();
    }

    @Test
    void createReturns400WhenSkuPatternInvalid() throws Exception {
        Long p = product("cake", true);
        problem(postJson(p, body("BAD SKU/1", 100, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").value(containsString("letters, digits"))));
    }

    @Test
    void createReturns400WhenSkuTooLong() throws Exception {
        Long p = product("cake", true);
        postJson(p, body("A".repeat(65), 100, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists());
    }

    @Test
    void createReturns400WhenPriceMissing() throws Exception {
        Long p = product("cake", true);
        postJson(p, "{\"sku\":\"S\",\"label\":\"L\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.priceMinor").exists());
    }

    @Test
    void createReturns400WithErrorsOnCompareAtWhenEqualToPrice() throws Exception {
        Long p = product("cake", true);
        problem(postJson(p, body("EQ", 1000, 1000L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.compareAtPriceMinor").exists()));
    }

    @Test
    void createReturns400WithErrorsOnCompareAtWhenBelowPrice() throws Exception {
        Long p = product("cake", true);
        postJson(p, body("LOW", 1000, 10L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.compareAtPriceMinor").exists());
        assertThat(variants.count()).isZero();
    }

    @Test
    void createReturns400WhenLabelIsOnlyNonBreakingSpaces() throws Exception {
        Long p = product("cake", true);
        postJson(p, "{\"sku\":\"S\",\"label\":\"  \",\"priceMinor\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.label").exists());
    }

    @Test
    void createReturns400ForMalformedJson() throws Exception {
        Long p = product("cake", true);
        problem(postJson(p, "{\"sku\":").andExpect(status().isBadRequest()));
    }

    @Test
    void createReturns400ForNonNumericProductId() throws Exception {
        problem(postJson("abc", body("S", 1, null)).andExpect(status().isBadRequest()));
    }

    @Test
    void createReturns404ForUnknownProduct() throws Exception {
        problem(postJson(999_999, body("S", 1, null)).andExpect(status().isNotFound()));
    }

    @Test
    void createReturns409ForDuplicateSku() throws Exception {
        Long p = product("cake", true);
        create(p, "DUP");
        problem(postJson(p, body("DUP", 1, null)).andExpect(status().isConflict()));
        assertThat(variants.count()).isEqualTo(1);
    }

    @Test
    void createReturns409ForDuplicateSkuUnderAnotherProduct() throws Exception {
        Long a = product("a", true);
        Long b = product("b", true);
        create(a, "DUP");
        postJson(b, body("DUP", 1, null)).andExpect(status().isConflict());
    }

    @Test
    void createReturns409ForActiveVariantUnderInactiveProduct() throws Exception {
        Long p = product("cake", false);
        problem(postJson(p, body("S", 1, null)).andExpect(status().isConflict()));
    }

    @Test
    void createReturns201ForInactiveVariantUnderInactiveProduct() throws Exception {
        Long p = product("cake", false);
        postJson(p, "{\"sku\":\"S\",\"label\":\"L\",\"priceMinor\":1,\"active\":false}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ---- GET list ----

    @Test
    void listIncludesInactiveByDefaultAndHidesThemWhenIncludeInactiveFalse() throws Exception {
        Long p = product("cake", true);
        long a = create(p, "A");
        long b = create(p, "B");
        mvc.perform(delete(base(p) + "/" + b)).andExpect(status().isNoContent());

        mvc.perform(get(base(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(a))
                .andExpect(jsonPath("$[1].id").value(b))
                .andExpect(jsonPath("$[1].active").value(false));
        mvc.perform(get(base(p)).param("includeInactive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku").value("A"));
    }

    @Test
    void listReturnsEmptyArrayForProductWithoutVariants() throws Exception {
        Long p = product("cake", true);
        mvc.perform(get(base(p))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listReturns404ForUnknownProduct() throws Exception {
        problem(mvc.perform(get(base(999_999))).andExpect(status().isNotFound()));
    }

    @Test
    void listReturns400ForInvalidIncludeInactive() throws Exception {
        Long p = product("cake", true);
        problem(mvc.perform(get(base(p)).param("includeInactive", "maybe")).andExpect(status().isBadRequest()));
    }

    // ---- GET by id ----

    @Test
    void getReturnsVariant() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "G");
        mvc.perform(get(base(p) + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sku").value("G"))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void getReturns404ForUnknownVariant() throws Exception {
        Long p = product("cake", true);
        problem(mvc.perform(get(base(p) + "/999999")).andExpect(status().isNotFound()));
    }

    @Test
    void getReturns404WhenVariantBelongsToAnotherProduct() throws Exception {
        Long a = product("a", true);
        Long b = product("b", true);
        long id = create(a, "A1");
        problem(mvc.perform(get(base(b) + "/" + id)).andExpect(status().isNotFound()));
    }

    // ---- PUT ----

    @Test
    void updateReplacesFieldsAndClearsOmittedCompareAt() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "OLD");
        putJson(p, id, updateBody("NEW", 90000, null, false, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sku").value("NEW"))
                .andExpect(jsonPath("$.label").value("1 kg"))
                .andExpect(jsonPath("$.priceMinor").value(90000))
                .andExpect(jsonPath("$.compareAtPriceMinor").doesNotExist())
                .andExpect(jsonPath("$.inStock").value(false))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.currency").value("INR"));
        mvc.perform(get(base(p) + "/" + id)).andExpect(jsonPath("$.sku").value("NEW"));
    }

    @Test
    void updateWithSameSkuSucceeds() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "SAME");
        putJson(p, id, updateBody("SAME", 100, 200L, true, true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.compareAtPriceMinor").value(200));
    }

    @Test
    void updateReturns400WhenInStockOrActiveMissing() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "V");
        problem(putJson(p, id, "{\"sku\":\"V\",\"label\":\"L\",\"priceMinor\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.inStock").exists())
                .andExpect(jsonPath("$.errors.active").exists()));
    }

    @Test
    void updateReturns400ForInvalidSkuAndNegativePrice() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "V");
        putJson(p, id, updateBody("bad sku", -1, null, true, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.priceMinor").exists());
    }

    @Test
    void updateReturns400WithErrorsOnCompareAtWhenNotGreaterThanPrice() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "V");
        putJson(p, id, updateBody("V", 500, 400L, true, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.compareAtPriceMinor").exists());
        assertThat(variants.findById(id).orElseThrow().getPriceMinor()).isEqualTo(65000);
    }

    @Test
    void updateReturns404ForUnknownVariant() throws Exception {
        Long p = product("cake", true);
        problem(putJson(p, 999_999, updateBody("V", 1, null, true, true)).andExpect(status().isNotFound()));
    }

    @Test
    void updateReturns404ForUnknownProduct() throws Exception {
        putJson(999_999, 1, updateBody("V", 1, null, true, true)).andExpect(status().isNotFound());
    }

    @Test
    void updateReturns404WhenVariantBelongsToAnotherProduct() throws Exception {
        Long a = product("a", true);
        Long b = product("b", true);
        long id = create(a, "A1");
        putJson(b, id, updateBody("A1", 1, null, true, true)).andExpect(status().isNotFound());
        assertThat(variants.findById(id).orElseThrow().getLabel()).isEqualTo("500 g");
    }

    @Test
    void updateReturns409WhenSkuTakenByAnotherVariant() throws Exception {
        Long p = product("cake", true);
        create(p, "TAKEN");
        long id = create(p, "MINE");
        problem(putJson(p, id, updateBody("TAKEN", 1, null, true, true)).andExpect(status().isConflict()));
        assertThat(variants.findById(id).orElseThrow().getSku()).isEqualTo("MINE");
    }

    @Test
    void updateReturns409WhenActivatingVariantUnderInactiveProduct() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "V");
        mvc.perform(delete("/api/v1/admin/products/" + p)).andExpect(status().isNoContent());
        problem(putJson(p, id, updateBody("V", 1, null, true, true)).andExpect(status().isConflict()));
        putJson(p, id, updateBody("V", 1, null, true, false)).andExpect(status().isOk());
    }

    // ---- DELETE ----

    @Test
    void deleteReturns204AndSoftDeletes() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "D");
        mvc.perform(delete(base(p) + "/" + id)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(variants.findById(id).orElseThrow().isActive()).isFalse();
        mvc.perform(get(base(p) + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteAlreadyInactiveVariantReturns204() throws Exception {
        Long p = product("cake", true);
        long id = create(p, "D");
        mvc.perform(delete(base(p) + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete(base(p) + "/" + id)).andExpect(status().isNoContent());
        assertThat(variants.findById(id).orElseThrow().isActive()).isFalse();
    }

    @Test
    void deleteReturns404ForUnknownVariant() throws Exception {
        Long p = product("cake", true);
        problem(mvc.perform(delete(base(p) + "/999999")).andExpect(status().isNotFound()));
    }

    @Test
    void deleteReturns404ForUnknownProduct() throws Exception {
        mvc.perform(delete(base(999_999) + "/1")).andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns404WhenVariantBelongsToAnotherProduct() throws Exception {
        Long a = product("a", true);
        Long b = product("b", true);
        long id = create(a, "A1");
        mvc.perform(delete(base(b) + "/" + id)).andExpect(status().isNotFound());
        assertThat(variants.findById(id).orElseThrow().isActive()).isTrue();
    }
}

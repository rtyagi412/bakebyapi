package com.bakeby.bakebyapi.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Task 7.1: end-to-end HTTP checks for the spec's "Edge Cases &amp; Error Handling" list and FR 7, against
 * Testcontainers Postgres.
 */
class CatalogEdgeCaseTests extends AbstractIntegrationTest {

    private static final String ADMIN_CATEGORIES = "/api/v1/admin/categories";
    private static final String ADMIN_PRODUCTS = "/api/v1/admin/products";
    private static final String PUBLIC_CATEGORIES = "/api/v1/categories";
    private static final String PUBLIC_PRODUCTS = "/api/v1/products";
    private static final String PROBLEM = "application/problem+json";

    private static final String DETAILS = "{\"description\":\"Rich\",\"ingredients\":\"Cocoa\","
            + "\"allergens\":[\"EGGS\"],\"dietaryType\":\"VEG\",\"shelfLifeDays\":2,\"leadTimeHours\":24,\"serves\":6}";

    @Autowired MockMvc mvc;
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

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long id(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long createCategory(String name, String slug, boolean active) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"active\":" + active + "}";
        return id(postJson(ADMIN_CATEGORIES, body).andExpect(status().isCreated()).andReturn());
    }

    private static String productBody(String name, String slug, long categoryId, boolean active) {
        return "{\"name\":\"" + name + "\"" + (slug == null ? "" : ",\"slug\":\"" + slug + "\"")
                + ",\"categoryId\":" + categoryId + ",\"active\":" + active + ",\"details\":" + DETAILS + "}";
    }

    private long createProduct(String name, String slug, long categoryId, boolean active) throws Exception {
        return id(postJson(ADMIN_PRODUCTS, productBody(name, slug, categoryId, active))
                .andExpect(status().isCreated()).andReturn());
    }

    private static String variantsUrl(long productId) {
        return ADMIN_PRODUCTS + "/" + productId + "/variants";
    }

    private static String variantBody(String sku, boolean active) {
        return "{\"sku\":\"" + sku + "\",\"label\":\"500 g\",\"priceMinor\":65000,\"active\":" + active + "}";
    }

    private long createVariant(long productId, String sku, boolean active) throws Exception {
        return id(postJson(variantsUrl(productId), variantBody(sku, active)).andExpect(status().isCreated())
                .andReturn());
    }

    private static void assertProblem(ResultActions actions, int status) throws Exception {
        actions.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    /** Fires the given POST bodies at the same URL concurrently, released together, and returns the statuses. */
    private List<MvcResult> concurrentPosts(String url, List<String> bodies) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(bodies.size());
        CountDownLatch ready = new CountDownLatch(bodies.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (String body : bodies) {
                Callable<MvcResult> call = () -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
                };
                futures.add(pool.submit(call));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<Integer> statuses(List<MvcResult> results) {
        return results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
    }

    private static void assertOneCreatedOneConflictProblem(List<MvcResult> results) throws Exception {
        assertThat(statuses(results)).containsExactly(201, 409);
        MvcResult conflict = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst()
                .orElseThrow();
        assertThat(conflict.getResponse().getContentType()).startsWith(PROBLEM);
        String body = conflict.getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.status")).intValue()).isEqualTo(409);
        assertThat(body).doesNotContain("trace").doesNotContain("SQLState").doesNotContain("uk_");
    }

    // ---- size clamped to 100 ----

    @Test
    void publicCategoryListClampsSizeAbove100To100() throws Exception {
        createCategory("Cakes", "cakes", true);
        mvc.perform(get(PUBLIC_CATEGORIES).param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void publicProductListClampsSizeAbove100To100() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        createProduct("Truffle", null, cat, true);
        mvc.perform(get(PUBLIC_PRODUCTS).param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void adminCategoryListClampsSizeAbove100To100() throws Exception {
        mvc.perform(get(ADMIN_CATEGORIES).param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void adminProductListClampsSizeAbove100To100() throws Exception {
        mvc.perform(get(ADMIN_PRODUCTS).param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listKeepsSizeOfExactly100() throws Exception {
        mvc.perform(get(PUBLIC_PRODUCTS).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // ---- unknown sort field ----

    @Test
    void everyListReturns400ProblemForUnknownSortField() throws Exception {
        for (String url : List.of(PUBLIC_CATEGORIES, PUBLIC_PRODUCTS, ADMIN_CATEGORIES, ADMIN_PRODUCTS)) {
            assertProblem(mvc.perform(get(url).param("sort", "password,asc")), 400);
            mvc.perform(get(url).param("sort", "password,asc"))
                    .andExpect(jsonPath("$.detail", containsString("password")));
        }
    }

    @Test
    void listReturns400ProblemForUnknownSortDirection() throws Exception {
        assertProblem(mvc.perform(get(PUBLIC_PRODUCTS).param("sort", "name,sideways")), 400);
    }

    // ---- malformed JSON ----

    @Test
    void categoryCreateReturns400ProblemForMalformedJson() throws Exception {
        assertProblem(postJson(ADMIN_CATEGORIES, "{\"name\":"), 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM category", Long.class)).isZero();
    }

    @Test
    void productCreateReturns400ProblemForMalformedJson() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        assertProblem(postJson(ADMIN_PRODUCTS, "{\"name\":\"Cake\",\"categoryId\":" + cat + ",\"details\":{"), 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Long.class)).isZero();
    }

    @Test
    void variantCreateReturns400ProblemForMalformedJson() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        long p = createProduct("Cake", null, cat, true);
        assertProblem(postJson(variantsUrl(p), "{\"sku\":\"A\",\"priceMinor\":"), 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM variant", Long.class)).isZero();
    }

    @Test
    void adminCreatesReturn400ProblemForEmptyBody() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        long p = createProduct("Cake", null, cat, true);
        for (String url : List.of(ADMIN_CATEGORIES, ADMIN_PRODUCTS, variantsUrl(p))) {
            assertProblem(postJson(url, ""), 400);
        }
    }

    @Test
    void productCreateReturns400ProblemForWrongJsonType() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        assertProblem(postJson(ADMIN_PRODUCTS,
                "{\"name\":\"Cake\",\"categoryId\":\"not-a-number\",\"details\":" + DETAILS + "}"), 400);
    }

    // ---- concurrent duplicate creates ----

    @Test
    void concurrentCategoryCreatesWithSameSlugReturnOne201AndOne409() throws Exception {
        List<MvcResult> results = concurrentPosts(ADMIN_CATEGORIES, List.of(
                "{\"name\":\"Cakes A\",\"slug\":\"cakes\"}",
                "{\"name\":\"Cakes B\",\"slug\":\"cakes\"}"));
        assertOneCreatedOneConflictProblem(results);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM category WHERE slug = 'cakes'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentProductCreatesWithSameExplicitSlugReturnOne201AndOne409() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        List<MvcResult> results = concurrentPosts(ADMIN_PRODUCTS, List.of(
                productBody("Truffle A", "truffle", cat, true),
                productBody("Truffle B", "truffle", cat, true)));
        assertOneCreatedOneConflictProblem(results);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product WHERE slug = 'truffle'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentProductCreatesWithSameNameNeverFailWith500OrDuplicateSlugs() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        List<MvcResult> results = concurrentPosts(ADMIN_PRODUCTS, List.of(
                productBody("Truffle", null, cat, true),
                productBody("Truffle", null, cat, true)));
        // Serialized: both 201 (truffle, truffle-2). Interleaved: derived slugs collide, the DB rejects one (409).
        assertThat(statuses(results)).isIn(List.of(201, 201), List.of(201, 409));
        List<String> slugs = jdbc.queryForList("SELECT slug FROM product ORDER BY slug", String.class);
        assertThat(slugs).doesNotHaveDuplicates().contains("truffle");
        for (MvcResult r : results) {
            if (r.getResponse().getStatus() == 409) {
                assertThat(r.getResponse().getContentType()).startsWith(PROBLEM);
            }
        }
    }

    @Test
    void concurrentVariantCreatesWithSameSkuReturnOne201AndOne409() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        long p = createProduct("Cake", null, cat, true);
        List<MvcResult> results = concurrentPosts(variantsUrl(p), List.of(
                variantBody("DUP-SKU", true),
                variantBody("DUP-SKU", true)));
        assertOneCreatedOneConflictProblem(results);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM variant WHERE sku = 'DUP-SKU'", Long.class))
                .isEqualTo(1);
    }

    // ---- all-digit slugs ----

    @Test
    void categoryCreateRejectsAllDigitSlugWith400Problem() throws Exception {
        ResultActions r = postJson(ADMIN_CATEGORIES, "{\"name\":\"Numbers\",\"slug\":\"12345\"}");
        assertProblem(r, 400);
        r.andExpect(jsonPath("$.errors.slug").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM category", Long.class)).isZero();
    }

    @Test
    void productCreateRejectsAllDigitSlugWith400Problem() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        ResultActions r = postJson(ADMIN_PRODUCTS, productBody("Numbers", "2024", cat, true));
        assertProblem(r, 400);
        r.andExpect(jsonPath("$.errors.slug").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Long.class)).isZero();
    }

    @Test
    void slugWithDigitsAndALetterIsAccepted() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        postJson(ADMIN_PRODUCTS, productBody("Anniversary", "2024-cake", cat, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("2024-cake"));
        mvc.perform(get(PUBLIC_PRODUCTS + "/2024-cake")).andExpect(status().isOk());
    }

    // ---- product with zero variants publicly visible ----

    @Test
    void productWithZeroVariantsIsShownPubliclyWithEmptyVariantsOnListAndGet() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        long p = createProduct("Plain Sponge", null, cat, true);
        mvc.perform(get(PUBLIC_PRODUCTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(p))
                .andExpect(jsonPath("$.content[0].variants").isArray())
                .andExpect(jsonPath("$.content[0].variants", hasSize(0)));
        for (String key : List.of(String.valueOf(p), "plain-sponge")) {
            mvc.perform(get(PUBLIC_PRODUCTS + "/" + key))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(p))
                    .andExpect(jsonPath("$.variants").isArray())
                    .andExpect(jsonPath("$.variants", hasSize(0)));
        }
    }

    // ---- deleting already-inactive entities ----

    @Test
    void deletingCategoryCreatedInactiveReturns204AndStaysInactive() throws Exception {
        long cat = createCategory("Seasonal", "seasonal", false);
        mvc.perform(delete(ADMIN_CATEGORIES + "/" + cat)).andExpect(status().isNoContent());
        mvc.perform(get(ADMIN_CATEGORIES + "/" + cat)).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deletingProductCreatedInactiveReturns204AndStaysInactive() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        long p = createProduct("Draft", null, cat, false);
        mvc.perform(delete(ADMIN_PRODUCTS + "/" + p)).andExpect(status().isNoContent());
        mvc.perform(get(ADMIN_PRODUCTS + "/" + p)).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deletingVariantCreatedInactiveReturns204AndStaysInactive() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        long p = createProduct("Cake", null, cat, true);
        long v = createVariant(p, "OFF-1", false);
        mvc.perform(delete(variantsUrl(p) + "/" + v)).andExpect(status().isNoContent());
        mvc.perform(get(variantsUrl(p) + "/" + v)).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deletingAlreadyDeletedEntitiesAgainReturns204() throws Exception {
        long cat = createCategory("Cakes", "cakes", true);
        long p = createProduct("Cake", null, cat, true);
        long v = createVariant(p, "V-1", true);
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(variantsUrl(p) + "/" + v)).andExpect(status().isNoContent());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(ADMIN_PRODUCTS + "/" + p)).andExpect(status().isNoContent());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(ADMIN_CATEGORIES + "/" + cat)).andExpect(status().isNoContent());
        }
    }

    @Test
    void deletingUnknownEntitiesStillReturns404Problem() throws Exception {
        assertProblem(mvc.perform(delete(ADMIN_CATEGORIES + "/999999")), 404);
        assertProblem(mvc.perform(delete(ADMIN_PRODUCTS + "/999999")), 404);
    }
}

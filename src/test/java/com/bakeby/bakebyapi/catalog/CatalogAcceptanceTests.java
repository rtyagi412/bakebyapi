package com.bakeby.bakebyapi.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Task 7.2: walks the catalog acceptance criteria AC 1 to 8 in order against Testcontainers Postgres, through
 * the full MVC + security filter chain with no credentials. Steps share state (ids) so the class uses one
 * instance and an explicit method order; the catalog tables are wiped before and after the class, and names
 * and slugs carry an "acc-" prefix.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogAcceptanceTests extends AbstractIntegrationTest {

    private static final String ADMIN = "/api/v1/admin";
    private static final String PUBLIC = "/api/v1";
    private static final String PROBLEM = "application/problem+json";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long categoryId;
    private long productId;
    private long variantId;
    private long derivedFirstId;
    private long derivedSecondId;
    private long pagingCategoryId;

    @BeforeAll
    void emptyDatabase() {
        wipe();
    }

    @AfterAll
    void cleanUp() {
        wipe();
    }

    private void wipe() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long id(MvcResult r) throws Exception {
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String product(String name, String slug, long categoryId) {
        return "{\"name\":\"" + name + "\"" + (slug == null ? "" : ",\"slug\":\"" + slug + "\"")
                + ",\"categoryId\":" + categoryId
                + ",\"details\":{\"description\":\"Dark chocolate sponge\",\"ingredients\":\"Flour, cocoa, eggs\","
                + "\"allergens\":[\"GLUTEN\",\"EGGS\"],\"dietaryType\":\"VEG\",\"shelfLifeDays\":3,"
                + "\"leadTimeHours\":24,\"serves\":8},"
                + "\"images\":[\"https://cdn.example.com/acc-truffle-1.jpg\","
                + "\"https://cdn.example.com/acc-truffle-2.jpg\"]}";
    }

    @Test
    @Order(1)
    @DisplayName("AC1: admin creates category, product with details/images and variant; public GET shows all")
    void ac1CreateCategoryProductAndVariantThenReadPublicly() throws Exception {
        MvcResult c = postJson(ADMIN + "/categories",
                "{\"name\":\"Acc Cakes\",\"slug\":\"acc-cakes\",\"description\":\"All cakes\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acc-cakes"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        categoryId = id(c);
        assertThat(c.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith(ADMIN + "/categories/" + categoryId);

        MvcResult p = postJson(ADMIN + "/products", product("Acc Chocolate Truffle", "acc-truffle", categoryId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acc-truffle"))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andReturn();
        productId = id(p);
        assertThat(p.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith(ADMIN + "/products/" + productId);

        MvcResult v = postJson(ADMIN + "/products/" + productId + "/variants",
                "{\"sku\":\"ACC-TRF-1KG\",\"label\":\"1 kg\",\"priceMinor\":120000,\"compareAtPriceMinor\":140000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.inStock").value(true))
                .andReturn();
        variantId = id(v);
        assertThat(v.getResponse().getHeader(HttpHeaders.LOCATION))
                .endsWith(ADMIN + "/products/" + productId + "/variants/" + variantId);

        mvc.perform(get(PUBLIC + "/products/acc-truffle"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.name").value("Acc Chocolate Truffle"))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.details.description").value("Dark chocolate sponge"))
                .andExpect(jsonPath("$.details.ingredients").value("Flour, cocoa, eggs"))
                .andExpect(jsonPath("$.details.allergens", containsInAnyOrder("GLUTEN", "EGGS")))
                .andExpect(jsonPath("$.details.dietaryType").value("VEG"))
                .andExpect(jsonPath("$.details.shelfLifeDays").value(3))
                .andExpect(jsonPath("$.details.leadTimeHours").value(24))
                .andExpect(jsonPath("$.details.serves").value(8))
                .andExpect(jsonPath("$.images[0]").value("https://cdn.example.com/acc-truffle-1.jpg"))
                .andExpect(jsonPath("$.images[1]").value("https://cdn.example.com/acc-truffle-2.jpg"))
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].id").value(variantId))
                .andExpect(jsonPath("$.variants[0].sku").value("ACC-TRF-1KG"))
                .andExpect(jsonPath("$.variants[0].label").value("1 kg"))
                .andExpect(jsonPath("$.variants[0].priceMinor").value(120000))
                .andExpect(jsonPath("$.variants[0].compareAtPriceMinor").value(140000))
                .andExpect(jsonPath("$.variants[0].currency").value("INR"));

        mvc.perform(get(PUBLIC + "/products/" + productId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acc-truffle"));
        mvc.perform(get(PUBLIC + "/categories/acc-cakes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId));
        mvc.perform(get(PUBLIC + "/products").param("category", "acc-cakes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) productId)));
    }

    @Test
    @Order(2)
    @DisplayName("AC2: slug derived from name; same name again gets -2")
    void ac2SlugIsDerivedAndSecondSameNameGetsSuffix() throws Exception {
        derivedFirstId = id(postJson(ADMIN + "/products", product("Acc Red Velvet", null, categoryId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acc-red-velvet"))
                .andReturn());
        derivedSecondId = id(postJson(ADMIN + "/products", product("Acc Red Velvet", null, categoryId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acc-red-velvet-2"))
                .andReturn());
        assertThat(derivedSecondId).isNotEqualTo(derivedFirstId);
        mvc.perform(get(PUBLIC + "/products/acc-red-velvet-2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(derivedSecondId));
    }

    @Test
    @Order(3)
    @DisplayName("AC3: soft-deleted product is 404 publicly by id and slug, absent from list, admin still sees it")
    void ac3SoftDeletedProductIsHiddenPubliclyButVisibleToAdmin() throws Exception {
        mvc.perform(delete(ADMIN + "/products/" + derivedSecondId)).andExpect(status().isNoContent());

        for (String key : new String[] {String.valueOf(derivedSecondId), "acc-red-velvet-2"}) {
            mvc.perform(get(PUBLIC + "/products/" + key))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                    .andExpect(jsonPath("$.status").value(404));
        }
        mvc.perform(get(PUBLIC + "/products").param("category", "acc-cakes").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem((int) derivedSecondId))))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) derivedFirstId)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get(ADMIN + "/products/" + derivedSecondId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.slug").value("acc-red-velvet-2"));
        mvc.perform(get(ADMIN + "/products").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) derivedSecondId)));
    }

    @Test
    @Order(4)
    @DisplayName("AC4: deleting a category with an active product is 409")
    void ac4DeletingCategoryWithActiveProductReturns409() throws Exception {
        mvc.perform(delete(ADMIN + "/categories/" + categoryId))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").isString());
        mvc.perform(get(PUBLIC + "/categories/" + categoryId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @Order(5)
    @DisplayName("AC5: duplicate SKU is 409 ProblemDetail")
    void ac5DuplicateSkuReturns409Problem() throws Exception {
        postJson(ADMIN + "/products/" + derivedFirstId + "/variants",
                "{\"sku\":\"ACC-TRF-1KG\",\"label\":\"1 kg\",\"priceMinor\":99000}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.trace").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM variant WHERE sku = 'ACC-TRF-1KG'", Long.class))
                .isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("AC6: blank name, negative price and compareAt <= price are 400 with field errors")
    void ac6InvalidBodiesReturn400WithFieldErrors() throws Exception {
        postJson(ADMIN + "/categories", "{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.name").isString());
        postJson(ADMIN + "/products", product("", null, categoryId))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.name").isString());

        String variants = ADMIN + "/products/" + productId + "/variants";
        postJson(variants, "{\"sku\":\"ACC-NEG\",\"label\":\"500 g\",\"priceMinor\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.priceMinor").isString());
        postJson(variants, "{\"sku\":\"ACC-CMP-EQ\",\"label\":\"500 g\",\"priceMinor\":50000,"
                + "\"compareAtPriceMinor\":50000}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.compareAtPriceMinor").isString());
        postJson(variants, "{\"sku\":\"ACC-CMP-LT\",\"label\":\"500 g\",\"priceMinor\":50000,"
                + "\"compareAtPriceMinor\":40000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.compareAtPriceMinor").isString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM variant WHERE sku LIKE 'ACC-%' AND sku <> 'ACC-TRF-1KG'",
                Long.class)).isZero();
    }

    @Test
    @Order(7)
    @DisplayName("AC7: 25 products, size=10&page=2 gives 5 items and correct page metadata")
    void ac7PaginationOf25ProductsReturnsLastFiveOnPage2() throws Exception {
        pagingCategoryId = id(postJson(ADMIN + "/categories", "{\"name\":\"Acc Paging\",\"slug\":\"acc-paging\"}")
                .andExpect(status().isCreated()).andReturn());
        for (int i = 1; i <= 25; i++) {
            postJson(ADMIN + "/products", product(String.format("Acc Page Item %02d", i), null, pagingCategoryId))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get(PUBLIC + "/products").param("category", "acc-paging")
                        .param("size", "10").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Acc Page Item 21"))
                .andExpect(jsonPath("$.content[4].name").value("Acc Page Item 25"));
    }

    @Test
    @Order(8)
    @DisplayName("AC8: migrations applied and unauthenticated reads and writes succeed")
    void ac8FlywayAppliedAndUnauthenticatedRequestsSucceed() throws Exception {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);

        // No Authorization header, no session, no CSRF token.
        MvcResult c = postJson(ADMIN + "/categories", "{\"name\":\"Acc Open\",\"slug\":\"acc-open\"}")
                .andExpect(status().isCreated()).andReturn();
        long openId = id(c);
        mvc.perform(get(ADMIN + "/categories/" + openId)).andExpect(status().isOk());
        mvc.perform(get(PUBLIC + "/categories/acc-open")).andExpect(status().isOk());
        mvc.perform(get(PUBLIC + "/products")).andExpect(status().isOk());
        mvc.perform(delete(ADMIN + "/categories/" + openId)).andExpect(status().isNoContent());
    }
}

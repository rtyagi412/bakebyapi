package com.bakeby.bakebyapi.catalog.web;

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
import com.bakeby.bakebyapi.catalog.domain.Category;
import com.bakeby.bakebyapi.catalog.domain.Product;
import com.bakeby.bakebyapi.catalog.domain.Variant;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.repository.VariantRepository;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AdminProductControllerTests extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/admin/products";
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

    private Long category(String slug, boolean active) {
        return categories.saveAndFlush(Category.builder().name(slug).slug(slug).active(active).build()).getId();
    }

    private static final String DETAILS = "{\"description\":\"Rich\",\"ingredients\":\"Cocoa\","
            + "\"allergens\":[\"EGGS\",\"GLUTEN\",\"DAIRY\"],\"dietaryType\":\"VEG\","
            + "\"shelfLifeDays\":2,\"leadTimeHours\":24,\"serves\":6}";

    private static String body(String name, Long categoryId) {
        return "{\"name\":\"" + name + "\",\"categoryId\":" + categoryId + ",\"details\":" + DETAILS
                + ",\"images\":[\"https://e.com/a.jpg\",\"https://e.com/b.jpg\"]}";
    }

    private static String updateBody(String name, Long categoryId, boolean active) {
        return "{\"name\":\"" + name + "\",\"categoryId\":" + categoryId + ",\"active\":" + active
                + ",\"details\":" + DETAILS + ",\"images\":[\"https://e.com/a.jpg\"]}";
    }

    /** Body with the given details JSON and images JSON (or null to omit). */
    private static String bodyWith(Long categoryId, String details, String images) {
        return "{\"name\":\"Cake\",\"categoryId\":" + categoryId + ",\"details\":" + details
                + (images == null ? "" : ",\"images\":" + images) + "}";
    }

    private ResultActions postJson(String body) throws Exception {
        return mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putJson(Object id, String body) throws Exception {
        return mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createProduct(String name, Long categoryId) throws Exception {
        postJson(body(name, categoryId)).andExpect(status().isCreated());
        return latestId();
    }

    private long latestId() {
        return jdbc.queryForObject("SELECT max(id) FROM product", Long.class);
    }

    private Variant addVariant(long productId, String sku, boolean active) {
        Product p = products.findById(productId).orElseThrow();
        return variants.saveAndFlush(Variant.builder().product(p).sku(sku).label("500 g").priceMinor(65000)
                .compareAtPriceMinor(75000L).active(active).build());
    }

    private boolean variantActive(Long id) {
        return variants.findById(id).orElseThrow().isActive();
    }

    // ---- POST ----

    @Test
    void createReturns201WithLocationAndFullBody() throws Exception {
        Long cat = category("cakes", true);
        postJson(body("Chocolate Truffle Cake", cat))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/" + latestId())))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Chocolate Truffle Cake"))
                .andExpect(jsonPath("$.slug").value("chocolate-truffle-cake"))
                .andExpect(jsonPath("$.categoryId").value(cat))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.details.description").value("Rich"))
                .andExpect(jsonPath("$.details.ingredients").value("Cocoa"))
                .andExpect(jsonPath("$.details.allergens", hasSize(3)))
                .andExpect(jsonPath("$.details.allergens[0]").value("GLUTEN"))
                .andExpect(jsonPath("$.details.allergens[1]").value("DAIRY"))
                .andExpect(jsonPath("$.details.allergens[2]").value("EGGS"))
                .andExpect(jsonPath("$.details.dietaryType").value("VEG"))
                .andExpect(jsonPath("$.details.shelfLifeDays").value(2))
                .andExpect(jsonPath("$.details.leadTimeHours").value(24))
                .andExpect(jsonPath("$.details.serves").value(6))
                .andExpect(jsonPath("$.images[0]").value("https://e.com/a.jpg"))
                .andExpect(jsonPath("$.images[1]").value("https://e.com/b.jpg"))
                .andExpect(jsonPath("$.variants", hasSize(0)));
        mvc.perform(get(BASE + "/" + latestId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(2)));
    }

    @Test
    void createSecondProductWithSameNameGetsDashTwoSlug() throws Exception {
        Long cat = category("cakes", true);
        postJson(body("Plum Cake", cat)).andExpect(jsonPath("$.slug").value("plum-cake"));
        postJson(body("Plum Cake", cat)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("plum-cake-2"));
        postJson(body("Plum Cake", cat)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("plum-cake-3"));
    }

    @Test
    void createUsesExplicitSlug() throws Exception {
        Long cat = category("cakes", true);
        postJson("{\"name\":\"Cake\",\"slug\":\"my-cake\",\"categoryId\":" + cat + ",\"details\":" + DETAILS + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("my-cake"))
                .andExpect(jsonPath("$.images", hasSize(0)));
    }

    @Test
    void createReturns409WhenExplicitSlugExists() throws Exception {
        Long cat = category("cakes", true);
        createProduct("Cake", cat);
        postJson("{\"name\":\"Other\",\"slug\":\"cake\",\"categoryId\":" + cat + ",\"details\":" + DETAILS + "}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns400WithErrorsOnCategoryIdWhenCategoryUnknown() throws Exception {
        postJson(body("Cake", 999999L))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.categoryId").exists());
        assertThat(products.count()).isZero();
    }

    @Test
    void createReturns409ForActiveProductInInactiveCategory() throws Exception {
        Long cat = category("hidden", false);
        postJson(body("Cake", cat))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns201ForInactiveProductInInactiveCategory() throws Exception {
        Long cat = category("hidden", false);
        postJson("{\"name\":\"Cake\",\"active\":false,\"categoryId\":" + cat + ",\"details\":" + DETAILS + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void createReturns400WithErrorsMapForBlankNameMissingCategoryAndDetails() throws Exception {
        postJson("{\"name\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists())
                .andExpect(jsonPath("$.errors.details").exists());
    }

    @Test
    void createReturns400WhenNameIsOnlyNonBreakingSpaces() throws Exception {
        Long cat = category("cakes", true);
        postJson(body("  ", cat))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createReturns400WhenNameTooLong() throws Exception {
        Long cat = category("cakes", true);
        postJson(body("a".repeat(151), cat))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createReturns400WhenSlugPatternInvalid() throws Exception {
        Long cat = category("cakes", true);
        postJson("{\"name\":\"Cake\",\"slug\":\"Bad Slug\",\"categoryId\":" + cat + ",\"details\":" + DETAILS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.slug").exists());
    }

    @Test
    void createReturns400WhenSlugIsAllDigits() throws Exception {
        Long cat = category("cakes", true);
        postJson("{\"name\":\"Cake\",\"slug\":\"123\",\"categoryId\":" + cat + ",\"details\":" + DETAILS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns400WhenNameCannotDeriveSlug() throws Exception {
        Long cat = category("cakes", true);
        postJson(body("2024", cat))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value("Cannot derive a slug from the name; supply a slug"));
    }

    @Test
    void createReturns400WhenDietaryTypeMissing() throws Exception {
        Long cat = category("cakes", true);
        postJson(bodyWith(cat, "{\"serves\":2}", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['details.dietaryType']").exists());
    }

    @Test
    void createReturns400WhenServesBelowOne() throws Exception {
        Long cat = category("cakes", true);
        postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\",\"serves\":0}", null))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors['details.serves']").exists());
    }

    @Test
    void createReturns400WhenShelfLifeOrLeadTimeNegative() throws Exception {
        Long cat = category("cakes", true);
        postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\",\"shelfLifeDays\":-1,\"leadTimeHours\":-1}", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['details.shelfLifeDays']").exists())
                .andExpect(jsonPath("$.errors['details.leadTimeHours']").exists());
    }

    @Test
    void createAcceptsZeroShelfLifeAndLeadTimeAndServesOne() throws Exception {
        Long cat = category("cakes", true);
        postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\",\"shelfLifeDays\":0,\"leadTimeHours\":0,\"serves\":1}", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.details.shelfLifeDays").value(0))
                .andExpect(jsonPath("$.details.serves").value(1));
    }

    @Test
    void createReturns400WhenMoreThanTenImages() throws Exception {
        Long cat = category("cakes", true);
        String eleven = IntStream.rangeClosed(1, 11).mapToObj(i -> "\"https://e.com/" + i + ".jpg\"")
                .collect(Collectors.joining(",", "[", "]"));
        postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\"}", eleven))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.images").exists());
    }

    @Test
    void createAcceptsExactlyTenImagesInOrder() throws Exception {
        Long cat = category("cakes", true);
        String ten = IntStream.rangeClosed(1, 10).mapToObj(i -> "\"https://e.com/" + i + ".jpg\"")
                .collect(Collectors.joining(",", "[", "]"));
        postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\"}", ten))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images", hasSize(10)))
                .andExpect(jsonPath("$.images[9]").value("https://e.com/10.jpg"));
    }

    @Test
    void createReturns400WhenImageIsNotHttpUrl() throws Exception {
        Long cat = category("cakes", true);
        for (String bad : new String[] {"ftp://e.com/a.jpg", "not a url", "javascript:alert(1)", ""}) {
            postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\"}", "[\"https://e.com/ok.jpg\",\"" + bad + "\"]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                    .andExpect(jsonPath("$.errors['images[1]']").exists());
        }
        assertThat(products.count()).isZero();
    }

    @Test
    void createAcceptsHttpAndUppercaseSchemeImageUrls() throws Exception {
        Long cat = category("cakes", true);
        postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\"}", "[\"http://e.com/a.jpg\",\"HTTPS://e.com/b.jpg\"]"))
                .andExpect(status().isCreated());
    }

    @Test
    void createReturns400WhenImageUrlTooLong() throws Exception {
        Long cat = category("cakes", true);
        String longUrl = "https://e.com/" + "a".repeat(500);
        postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\"}", "[\"" + longUrl + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['images[0]']").exists());
    }

    @Test
    void createReturns400ForUnknownAllergen() throws Exception {
        Long cat = category("cakes", true);
        postJson(bodyWith(cat, "{\"dietaryType\":\"VEG\",\"allergens\":[\"GLUTEN\",\"CHOCOLATE\"]}", null))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns400ForUnknownDietaryType() throws Exception {
        Long cat = category("cakes", true);
        postJson(bodyWith(cat, "{\"dietaryType\":\"CARNIVORE\"}", null))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns400ForMalformedJson() throws Exception {
        postJson("{\"name\":")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createIgnoresInlineVariants() throws Exception {
        Long cat = category("cakes", true);
        postJson("{\"name\":\"Cake\",\"categoryId\":" + cat + ",\"details\":" + DETAILS
                + ",\"variants\":[{\"sku\":\"X\",\"label\":\"x\",\"priceMinor\":1}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants", hasSize(0)));
        assertThat(variants.count()).isZero();
    }

    // ---- GET list ----

    @Test
    void listIncludesInactiveByDefault() throws Exception {
        Long cat = category("cakes", true);
        createProduct("Apple", cat);
        long hidden = createProduct("Banana", cat);
        mvc.perform(delete(BASE + "/" + hidden)).andExpect(status().isNoContent());
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Apple"))
                .andExpect(jsonPath("$.content[1].name").value("Banana"))
                .andExpect(jsonPath("$.content[1].active").value(false))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listHidesInactiveWhenIncludeInactiveFalse() throws Exception {
        Long cat = category("cakes", true);
        createProduct("Apple", cat);
        long hidden = createProduct("Banana", cat);
        mvc.perform(delete(BASE + "/" + hidden)).andExpect(status().isNoContent());
        mvc.perform(get(BASE).param("includeInactive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Apple"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listEmbedsAllVariantsAndImagesPerProduct() throws Exception {
        Long cat = category("cakes", true);
        long a = createProduct("Apple", cat);
        long b = createProduct("Banana", cat);
        addVariant(a, "A-500G", true);
        addVariant(b, "B-500G", true);
        addVariant(b, "B-1KG", false);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].variants", hasSize(1)))
                .andExpect(jsonPath("$.content[0].variants[0].sku").value("A-500G"))
                .andExpect(jsonPath("$.content[1].variants", hasSize(2)))
                .andExpect(jsonPath("$.content[1].variants[1].sku").value("B-1KG"))
                .andExpect(jsonPath("$.content[1].variants[1].active").value(false))
                .andExpect(jsonPath("$.content[1].variants[1].priceMinor").value(65000))
                .andExpect(jsonPath("$.content[1].variants[1].compareAtPriceMinor").value(75000))
                .andExpect(jsonPath("$.content[1].variants[1].currency").value("INR"))
                .andExpect(jsonPath("$.content[1].variants[1].inStock").value(true))
                .andExpect(jsonPath("$.content[1].images", hasSize(2)));
    }

    @Test
    void listPaginatesWithCorrectMetadata() throws Exception {
        Long cat = category("cakes", true);
        for (int i = 0; i < 25; i++) {
            createProduct(String.format("Cake %02d", i), cat);
        }
        mvc.perform(get(BASE).param("size", "10").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].name").value("Cake 20"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void listSortsByNameDescending() throws Exception {
        Long cat = category("cakes", true);
        createProduct("Apple", cat);
        createProduct("Cherry", cat);
        createProduct("Banana", cat);
        mvc.perform(get(BASE).param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Cherry"))
                .andExpect(jsonPath("$.content[2].name").value("Apple"));
    }

    @Test
    void listReturns400ForUnknownSortField() throws Exception {
        mvc.perform(get(BASE).param("sort", "price"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listReturns400ForZeroSizeAndNonBooleanIncludeInactive() throws Exception {
        mvc.perform(get(BASE).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(BASE).param("includeInactive", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listResponseDoesNotLeakEntityInternals() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        addVariant(p, "C-1", true);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("createdAt"))))
                .andExpect(content().string(not(containsString("hibernateLazyInitializer"))))
                .andExpect(content().string(not(containsString("\"category\""))))
                .andExpect(content().string(not(containsString("\"product\""))));
    }

    // ---- GET by id ----

    @Test
    void getByIdReturns200WithVariants() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        addVariant(p, "C-1", true);
        mvc.perform(get(BASE + "/" + p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p))
                .andExpect(jsonPath("$.slug").value("cake"))
                .andExpect(jsonPath("$.variants[0].sku").value("C-1"));
    }

    @Test
    void getByIdReturnsSoftDeletedProductToAdmin() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        mvc.perform(delete(BASE + "/" + p)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/" + p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void getByIdReturns404ForMissing() throws Exception {
        mvc.perform(get(BASE + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getByIdReturns400ForNonNumericId() throws Exception {
        mvc.perform(get(BASE + "/cake"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // ---- PUT ----

    @Test
    void updateReturns200WithReplacedValues() throws Exception {
        Long cat = category("cakes", true);
        Long pies = category("pies", true);
        long p = createProduct("Cake", cat);
        putJson(p, "{\"name\":\"Apple Pie\",\"categoryId\":" + pies + ",\"active\":true,"
                + "\"details\":{\"dietaryType\":\"VEGAN\",\"allergens\":[\"NUTS\"],\"serves\":4},"
                + "\"images\":[\"https://e.com/pie.jpg\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Apple Pie"))
                .andExpect(jsonPath("$.slug").value("cake"))
                .andExpect(jsonPath("$.categoryId").value(pies))
                .andExpect(jsonPath("$.details.dietaryType").value("VEGAN"))
                .andExpect(jsonPath("$.details.allergens", hasSize(1)))
                .andExpect(jsonPath("$.details.description").doesNotExist())
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0]").value("https://e.com/pie.jpg"));
    }

    @Test
    void updateWithoutImagesOrAllergensClearsThem() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        putJson(p, "{\"name\":\"Cake\",\"categoryId\":" + cat + ",\"active\":true,"
                + "\"details\":{\"dietaryType\":\"VEG\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(0)))
                .andExpect(jsonPath("$.details.allergens", hasSize(0)));
        mvc.perform(get(BASE + "/" + p))
                .andExpect(jsonPath("$.images", hasSize(0)))
                .andExpect(jsonPath("$.details.allergens", hasSize(0)));
    }

    @Test
    void updateChangesSlugWhenSupplied() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        putJson(p, "{\"name\":\"Cake\",\"slug\":\"new-cake\",\"categoryId\":" + cat + ",\"active\":true,"
                + "\"details\":" + DETAILS + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("new-cake"));
    }

    @Test
    void updateReturns409WhenSlugTakenByOther() throws Exception {
        Long cat = category("cakes", true);
        createProduct("Cake", cat);
        long pie = createProduct("Pie", cat);
        putJson(pie, "{\"name\":\"Pie\",\"slug\":\"cake\",\"categoryId\":" + cat + ",\"active\":true,"
                + "\"details\":" + DETAILS + "}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns400WhenSlugIsAllDigits() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        putJson(p, "{\"name\":\"Cake\",\"slug\":\"42\",\"categoryId\":" + cat + ",\"active\":true,"
                + "\"details\":" + DETAILS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns400WithErrorsMapWhenNameBlankAndActiveMissing() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        putJson(p, "{\"name\":\"\",\"categoryId\":" + cat + ",\"details\":" + DETAILS + "}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.active").exists());
    }

    @Test
    void updateReturns400WhenImageInvalid() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        putJson(p, "{\"name\":\"Cake\",\"categoryId\":" + cat + ",\"active\":true,\"details\":" + DETAILS
                + ",\"images\":[\"ftp://e.com/x.jpg\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['images[0]']").exists());
    }

    @Test
    void updateReturns400WithErrorsOnCategoryIdWhenCategoryUnknown() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        putJson(p, updateBody("Cake", 999999L, true))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.categoryId").exists());
    }

    @Test
    void updateReturns404ForMissing() throws Exception {
        Long cat = category("cakes", true);
        putJson(999999, updateBody("Cake", cat, true))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns409WhenMovingActiveProductToInactiveCategory() throws Exception {
        Long cat = category("cakes", true);
        Long hidden = category("hidden", false);
        long p = createProduct("Cake", cat);
        putJson(p, updateBody("Cake", hidden, true))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns409WhenReactivatingUnderInactiveCategory() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        mvc.perform(delete(BASE + "/" + p)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/categories/" + cat)).andExpect(status().isNoContent());
        putJson(p, updateBody("Cake", cat, true))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(BASE + "/" + p)).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateReactivatesSoftDeletedProductWithoutReactivatingVariants() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        Variant v = addVariant(p, "C-1", true);
        mvc.perform(delete(BASE + "/" + p)).andExpect(status().isNoContent());
        putJson(p, updateBody("Cake", cat, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.variants[0].active").value(false));
        assertThat(variantActive(v.getId())).isFalse();
    }

    @Test
    void updateActiveFalseDeactivatesVariants() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        Variant v = addVariant(p, "C-1", true);
        putJson(p, updateBody("Cake", cat, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.variants[0].active").value(false));
        assertThat(variantActive(v.getId())).isFalse();
    }

    @Test
    void updateReturns400ForNonNumericIdAndMalformedJson() throws Exception {
        Long cat = category("cakes", true);
        putJson("abc", updateBody("Cake", cat, true))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        long p = createProduct("Cake", cat);
        putJson(p, "{\"name\":")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // ---- DELETE ----

    @Test
    void deleteReturns204AndSoftDeletesProductAndVariants() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        Variant v1 = addVariant(p, "C-1", true);
        Variant v2 = addVariant(p, "C-2", true);
        mvc.perform(delete(BASE + "/" + p)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(products.findById(p)).isPresent();
        assertThat(products.findById(p).orElseThrow().isActive()).isFalse();
        assertThat(variantActive(v1.getId())).isFalse();
        assertThat(variantActive(v2.getId())).isFalse();
    }

    @Test
    void deleteIsIdempotentReturning204Twice() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        mvc.perform(delete(BASE + "/" + p)).andExpect(status().isNoContent());
        mvc.perform(delete(BASE + "/" + p)).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404ForMissing() throws Exception {
        mvc.perform(delete(BASE + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void deleteReturns400ForNonNumericId() throws Exception {
        mvc.perform(delete(BASE + "/cake"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void deletingProductUnblocksCategoryDeletion() throws Exception {
        Long cat = category("cakes", true);
        long p = createProduct("Cake", cat);
        mvc.perform(delete("/api/v1/admin/categories/" + cat)).andExpect(status().isConflict());
        mvc.perform(delete(BASE + "/" + p)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/categories/" + cat)).andExpect(status().isNoContent());
    }

    @Test
    void errorResponsesDoNotLeakStackTraces() throws Exception {
        mvc.perform(get(BASE + "/999999"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))));
        postJson("{\"name\":")
                .andExpect(content().string(not(containsString("at com."))))
                .andExpect(content().string(not(containsString("trace"))));
    }
}

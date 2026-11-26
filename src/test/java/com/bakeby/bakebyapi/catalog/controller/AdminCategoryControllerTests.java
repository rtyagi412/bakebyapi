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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AdminCategoryControllerTests extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/admin/categories";
    static final String PROBLEM = "application/problem+json";

    @Autowired MockMvc mvc;
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

    private ResultActions postJson(String body) throws Exception {
        return mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putJson(Object id, String body) throws Exception {
        return mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long seed(String name, boolean active) {
        return categories.saveAndFlush(Category.builder().name(name)
                .slug(name.toLowerCase().replace(' ', '-')).active(active).build()).getId();
    }

    private long latestId() {
        return jdbc.queryForObject("SELECT max(id) FROM category", Long.class);
    }

    // ---- POST ----

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        postJson("{\"name\":\"Birthday Cakes\",\"description\":\"d\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Birthday Cakes"))
                .andExpect(jsonPath("$.slug").value("birthday-cakes"))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get(BASE + "/" + latestId())).andExpect(status().isOk());
    }

    @Test
    void createLocationHeaderPointsAtNewResource() throws Exception {
        postJson("{\"name\":\"Pies\"}").andExpect(status().isCreated());
        postJson("{\"name\":\"Tarts\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/" + latestId())));
    }

    @Test
    void createReturns400WithErrorsMapWhenNameBlank() throws Exception {
        postJson("{\"name\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createReturns400WithErrorsMapWhenSlugPatternInvalid() throws Exception {
        postJson("{\"name\":\"X\",\"slug\":\"Bad Slug\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.slug").exists());
    }

    @Test
    void createReturns400WhenNameTooLong() throws Exception {
        postJson("{\"name\":\"" + "a".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createReturns400WhenSlugIsAllDigits() throws Exception {
        postJson("{\"name\":\"X\",\"slug\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns409WhenSlugExists() throws Exception {
        postJson("{\"name\":\"A\",\"slug\":\"dup\"}").andExpect(status().isCreated());
        postJson("{\"name\":\"B\",\"slug\":\"dup\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns409WhenNameExistsIgnoringCase() throws Exception {
        postJson("{\"name\":\"Cakes\"}").andExpect(status().isCreated());
        postJson("{\"name\":\"cAkEs\",\"slug\":\"other\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns400ForMalformedJson() throws Exception {
        postJson("{\"name\":")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns400WhenBodyMissing() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns415ForUnsupportedContentType() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---- GET list ----

    @Test
    void listIncludesInactiveByDefault() throws Exception {
        seed("Active One", true);
        seed("Inactive One", false);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listHidesInactiveWhenIncludeInactiveFalse() throws Exception {
        seed("Active One", true);
        seed("Inactive One", false);
        mvc.perform(get(BASE).param("includeInactive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Active One"))
                .andExpect(jsonPath("$.content[0].active").value(true));
    }

    @Test
    void listReturnsExactlyPageResponseKeys() throws Exception {
        seed("A", true);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist())
                .andExpect(jsonPath("$.first").doesNotExist())
                .andExpect(jsonPath("$.last").doesNotExist())
                .andExpect(jsonPath("$.number").doesNotExist());
    }

    @Test
    void listIncludesInactiveWhenRequested() throws Exception {
        seed("Active One", true);
        seed("Inactive One", false);
        mvc.perform(get(BASE).param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void listPaginatesAndSortsByNameDescending() throws Exception {
        for (String n : new String[] {"A", "B", "C", "D", "E"}) {
            seed(n, true);
        }
        mvc.perform(get(BASE).param("page", "1").param("size", "2").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("C"))
                .andExpect(jsonPath("$.content[1].name").value("B"));
    }

    @Test
    void listClampsSizeAbove100To100() throws Exception {
        seed("A", true);
        mvc.perform(get(BASE).param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listReturns400ForUnknownSortField() throws Exception {
        mvc.perform(get(BASE).param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listReturns400ForNegativePage() throws Exception {
        mvc.perform(get(BASE).param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void listReturns400ForZeroSize() throws Exception {
        mvc.perform(get(BASE).param("size", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void listReturns400ForNonNumericPage() throws Exception {
        mvc.perform(get(BASE).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listResponseDoesNotLeakEntityInternals() throws Exception {
        seed("A", true);
        mvc.perform(get(BASE))
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andExpect(content().string(not(containsString("hibernate"))));
    }

    // ---- GET by id ----

    @Test
    void getByIdReturns200() throws Exception {
        long id = seed("Cakes", true);
        mvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Cakes"));
    }

    @Test
    void getByIdReturnsInactiveCategoryToAdmin() throws Exception {
        long id = seed("Gone", false);
        mvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void getByIdReturns404ForMissing() throws Exception {
        mvc.perform(get(BASE + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getByIdReturns400ForNonNumericId() throws Exception {
        mvc.perform(get(BASE + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // ---- PUT ----

    @Test
    void updateReturns200WithNewValues() throws Exception {
        long id = seed("Cakes", true);
        putJson(id, "{\"name\":\"Cakes 2\",\"description\":\"x\",\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cakes 2"))
                .andExpect(jsonPath("$.description").value("x"))
                .andExpect(jsonPath("$.slug").value("cakes"));
    }

    @Test
    void updateReactivatesInactiveCategory() throws Exception {
        long id = seed("Gone", false);
        putJson(id, "{\"name\":\"Gone\",\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateReturns400WithErrorsMapWhenNameBlankAndActiveMissing() throws Exception {
        long id = seed("Cakes", true);
        putJson(id, "{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.active").exists());
    }

    @Test
    void updateReturns400WhenSlugIsAllDigits() throws Exception {
        long id = seed("Cakes", true);
        putJson(id, "{\"name\":\"Cakes\",\"slug\":\"123\",\"active\":true}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturns404ForMissing() throws Exception {
        putJson(999999, "{\"name\":\"X\",\"active\":true}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns409WhenNameTakenByOtherIgnoringCase() throws Exception {
        seed("Cakes", true);
        long pies = seed("Pies", true);
        putJson(pies, "{\"name\":\"CAKES\",\"active\":true}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns409WhenSlugTakenByOther() throws Exception {
        seed("Cakes", true);
        long pies = seed("Pies", true);
        putJson(pies, "{\"name\":\"Pies\",\"slug\":\"cakes\",\"active\":true}")
                .andExpect(status().isConflict());
    }

    @Test
    void updateReturns200WhenKeepingOwnNameAndSlug() throws Exception {
        long id = seed("Cakes", true);
        putJson(id, "{\"name\":\"Cakes\",\"slug\":\"cakes\",\"active\":true}")
                .andExpect(status().isOk());
    }

    @Test
    void updateReturns400ForMalformedJson() throws Exception {
        long id = seed("Cakes", true);
        putJson(id, "{not json").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns400ForNonNumericId() throws Exception {
        putJson("abc", "{\"name\":\"X\",\"active\":true}").andExpect(status().isBadRequest());
    }

    // ---- DELETE ----

    @Test
    void deleteReturns204AndDeactivates() throws Exception {
        long id = seed("Cakes", true);
        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteIsIdempotentReturning204Twice() throws Exception {
        long id = seed("Cakes", true);
        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns409WhenCategoryHasActiveProducts() throws Exception {
        long id = seed("Cakes", true);
        Category c = categories.findById(id).orElseThrow();
        products.saveAndFlush(Product.builder().category(c).name("P").slug("p")
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build()).build());
        mvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deleteConflictBodyUsesDeactivateOrDeleteMessage() throws Exception {
        long id = seed("Cakes", true);
        addActiveProduct(id);
        mvc.perform(delete(BASE + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Category has active products and cannot be deactivated or deleted"));
    }

    private void addActiveProduct(long categoryId) {
        Category c = categories.findById(categoryId).orElseThrow();
        products.saveAndFlush(Product.builder().category(c).name("P").slug("p")
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build()).build());
    }

    // ---- new behaviors ----

    @Test
    void updateActiveFalseReturns409WhenCategoryHasActiveProducts() throws Exception {
        long id = seed("Cakes", true);
        addActiveProduct(id);
        putJson(id, "{\"name\":\"Cakes\",\"active\":false}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail")
                        .value("Category has active products and cannot be deactivated or deleted"));
        mvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateActiveFalseReturns200WhenNoActiveProducts() throws Exception {
        long id = seed("Cakes", true);
        putJson(id, "{\"name\":\"Cakes\",\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateActiveFalseReturns200WhenAlreadyInactive() throws Exception {
        long id = seed("Cakes", false);
        addActiveProduct(id);
        putJson(id, "{\"name\":\"Cakes\",\"active\":false}").andExpect(status().isOk());
    }

    @Test
    void createTrimsNameAndStoresTrimmed() throws Exception {
        postJson("{\"name\":\" Cakes \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cakes"))
                .andExpect(jsonPath("$.slug").value("cakes"));
    }

    @Test
    void createReturns409WhenTrimmedNameMatchesExistingIgnoringCase() throws Exception {
        seed("cakes", true);
        postJson("{\"name\":\" Cakes \",\"slug\":\"other\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void createReturns400WhenDescriptionTooLong() throws Exception {
        postJson("{\"name\":\"X\",\"description\":\"" + "d".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void createAcceptsDescriptionOfExactly2000Chars() throws Exception {
        postJson("{\"name\":\"X\",\"description\":\"" + "d".repeat(2000) + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturns400WhenDescriptionTooLong() throws Exception {
        long id = seed("Cakes", true);
        putJson(id, "{\"name\":\"Cakes\",\"active\":true,\"description\":\"" + "d".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void createReturns400WithDeriveMessageForNumericNameWithoutSlug() throws Exception {
        postJson("{\"name\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value("Cannot derive a slug from the name; supply a slug"));
    }

    @Test
    void createReturns400WithDeriveMessageForPunctuationNameWithoutSlug() throws Exception {
        postJson("{\"name\":\"!!!\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cannot derive a slug from the name; supply a slug"));
    }

    @Test
    void createReturns400WithInvalidSlugMessageForExplicitAllDigitSlug() throws Exception {
        postJson("{\"name\":\"X\",\"slug\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Invalid slug")));
    }

    @Test
    void updateReturns400NotConflictWhenSlugInvalidAndNameConflicts() throws Exception {
        seed("Cakes", true);
        long pies = seed("Pies", true);
        putJson(pies, "{\"name\":\"Cakes\",\"slug\":\"42\",\"active\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Invalid slug")));
    }

    @Test
    void updateOmittingDescriptionClearsIt() throws Exception {
        long id = seed("Cakes", true);
        putJson(id, "{\"name\":\"Cakes\",\"description\":\"old\",\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("old"));
        putJson(id, "{\"name\":\"Cakes\",\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
        mvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void listWithTiedSortKeyPagesInAscendingIdOrderWithoutDuplicatesOrGaps() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (String n : new String[] {"A", "B", "C", "D", "E"}) {
            ids.add(seed(n, true));
        }
        // Two tie groups: first two rows share a later created_at, last three an earlier one.
        jdbc.update("UPDATE category SET created_at = TIMESTAMPTZ '2024-01-02 00:00:00+00' WHERE id IN (?, ?)",
                ids.get(0), ids.get(1));
        jdbc.update("UPDATE category SET created_at = TIMESTAMPTZ '2024-01-01 00:00:00+00' WHERE id IN (?, ?, ?)",
                ids.get(2), ids.get(3), ids.get(4));
        List<Long> expected = List.of(ids.get(2), ids.get(3), ids.get(4), ids.get(0), ids.get(1));

        List<Long> seen = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            String body = mvc.perform(get(BASE).param("page", String.valueOf(p)).param("size", "2")
                            .param("sort", "createdAt"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(body);
            while (m.find()) {
                seen.add(Long.valueOf(m.group(1)));
            }
        }
        assertThat(seen).doesNotHaveDuplicates().containsExactlyElementsOf(expected);
    }

    @Test
    void createReturns409WhenNameWithNonBreakingSpacePaddingMatchesUnpaddedName() throws Exception {
        postJson("{\"name\":\"Cakes\"}").andExpect(status().isCreated());
        postJson("{\"name\":\"\u00A0Cakes\u00A0\",\"slug\":\"other\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void deleteReturns404ForMissing() throws Exception {
        mvc.perform(delete(BASE + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void deleteReturns400ForNonNumericId() throws Exception {
        mvc.perform(delete(BASE + "/abc")).andExpect(status().isBadRequest());
    }
}

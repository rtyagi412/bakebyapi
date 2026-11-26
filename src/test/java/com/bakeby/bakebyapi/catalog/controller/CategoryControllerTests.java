package com.bakeby.bakebyapi.catalog.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.entity.Category;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Public category read API: GET /api/v1/categories and /{idOrSlug}. */
class CategoryControllerTests extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/categories";
    static final String PROBLEM = "application/problem+json";

    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    private Category category(String name, String slug, boolean active) {
        return categories.saveAndFlush(Category.builder().name(name).slug(slug).description(name + " desc")
                .active(active).build());
    }

    // ---- list ----

    @Test
    void listReturnsOnlyActiveCategoriesSortedByNameByDefault() throws Exception {
        category("Pastries", "pastries", true);
        category("Breads", "breads", false);
        category("Cakes", "cakes", true);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].slug").value("cakes"))
                .andExpect(jsonPath("$.content[0].name").value("Cakes"))
                .andExpect(jsonPath("$.content[0].description").value("Cakes desc"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[1].slug").value("pastries"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listReturnsEmptyPageWhenNoActiveCategories() throws Exception {
        category("Breads", "breads", false);
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void listPaginatesAndReportsPageMetadata() throws Exception {
        for (int i = 0; i < 5; i++) {
            category("Cat " + i, "cat-" + i, true);
        }
        mvc.perform(get(BASE).param("size", "2").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("cat-4"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void listHonoursSortDescending() throws Exception {
        category("Alpha", "alpha", true);
        category("Zeta", "zeta", true);
        mvc.perform(get(BASE).param("sort", "slug,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("zeta"))
                .andExpect(jsonPath("$.content[1].slug").value("alpha"));
    }

    @Test
    void listReturns400ProblemForUnknownSortField() throws Exception {
        mvc.perform(get(BASE).param("sort", "active"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", containsString("Unknown sort field")));
    }

    @Test
    void listReturns400ProblemForNegativePage() throws Exception {
        mvc.perform(get(BASE).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listReturns400ProblemForZeroSize() throws Exception {
        mvc.perform(get(BASE).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listReturns400ProblemForNonNumericPage() throws Exception {
        mvc.perform(get(BASE).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void listClampsSizeToMaximum() throws Exception {
        category("Cakes", "cakes", true);
        mvc.perform(get(BASE).param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // ---- get by id / slug ----

    @Test
    void getByIdReturnsActiveCategory() throws Exception {
        Category c = category("Cakes", "cakes", true);
        mvc.perform(get(BASE + "/" + c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(c.getId()))
                .andExpect(jsonPath("$.name").value("Cakes"))
                .andExpect(jsonPath("$.slug").value("cakes"))
                .andExpect(jsonPath("$.description").value("Cakes desc"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getBySlugReturnsActiveCategory() throws Exception {
        Category c = category("Birthday Cakes", "birthday-cakes", true);
        mvc.perform(get(BASE + "/birthday-cakes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(c.getId()))
                .andExpect(jsonPath("$.slug").value("birthday-cakes"));
    }

    @Test
    void getByIdReturns404ProblemForInactiveCategory() throws Exception {
        Category c = category("Breads", "breads", false);
        mvc.perform(get(BASE + "/" + c.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getBySlugReturns404ProblemForInactiveCategory() throws Exception {
        category("Breads", "breads", false);
        mvc.perform(get(BASE + "/breads"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getByIdReturns404ProblemForMissingCategory() throws Exception {
        mvc.perform(get(BASE + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getBySlugReturns404ProblemForMissingCategory() throws Exception {
        mvc.perform(get(BASE + "/no-such-category"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getReturns404ForIdOverflowingLong() throws Exception {
        mvc.perform(get(BASE + "/99999999999999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void getWithNumericValueDoesNotMatchBySlug() throws Exception {
        // Slug lookups never happen for all-digit values; an id that doesn't exist is 404 even if
        // some other category exists.
        category("Cakes", "cakes", true);
        mvc.perform(get(BASE + "/0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicCategoryEndpointIsReadOnly() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }
}

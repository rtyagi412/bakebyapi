package com.bakeby.bakebyapi.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.domain.Category;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Simulates the check-then-insert race: the pre-check says "free" but a row exists, so the
 * database unique index must reject the write and the API must answer 409 problem+json.
 */
class CategoryConstraintRaceTests extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/categories";
    private static final String PROBLEM = "application/problem+json";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean CategoryRepository categories;

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    private long seed(String name, String slug) {
        return categories.saveAndFlush(Category.builder().name(name).slug(slug).active(true).build()).getId();
    }

    @Test
    void createReturns409ProblemJsonWhenNameDuplicateSlipsPastPreCheck() throws Exception {
        seed("Cakes", "cakes");
        doReturn(false).when(categories).existsByNameIgnoreCase(anyString());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CAKES\",\"slug\":\"other\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void createReturns409ProblemJsonWhenSlugDuplicateSlipsPastPreCheck() throws Exception {
        seed("Cakes", "cakes");
        doReturn(false).when(categories).existsBySlug(anyString());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pies\",\"slug\":\"cakes\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns409ProblemJsonWhenNameDuplicateSlipsPastPreCheck() throws Exception {
        seed("Cakes", "cakes");
        long pies = seed("Pies", "pies");
        doReturn(false).when(categories).existsByNameIgnoreCaseAndIdNot(anyString(), anyLong());
        mvc.perform(put(BASE + "/" + pies).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cakes\",\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updateReturns409ProblemJsonWhenSlugDuplicateSlipsPastPreCheck() throws Exception {
        seed("Cakes", "cakes");
        long pies = seed("Pies", "pies");
        doReturn(false).when(categories).existsBySlug(any());
        mvc.perform(put(BASE + "/" + pies).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pies\",\"slug\":\"cakes\",\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }
}

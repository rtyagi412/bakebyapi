package com.bakeby.bakebyapi.catalog.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakeby.bakebyapi.AbstractIntegrationTest;
import com.bakeby.bakebyapi.catalog.entity.Category;
import com.bakeby.bakebyapi.catalog.entity.DietaryType;
import com.bakeby.bakebyapi.catalog.entity.Product;
import com.bakeby.bakebyapi.catalog.entity.ProductDetails;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Simulates the product slug check-then-insert race deterministically: the pre-check says "free" but the slug
 * is taken, so the database unique constraint must reject the write and the API must answer 409 problem+json.
 */
class ProductConstraintRaceTests extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/products";
    private static final String PROBLEM = "application/problem+json";
    private static final String DETAILS = "{\"dietaryType\":\"VEG\"}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CategoryRepository categories;
    @MockitoSpyBean ProductRepository products;

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    private Category category() {
        return categories.saveAndFlush(Category.builder().name("Cakes").slug("cakes").active(true).build());
    }

    private long seed(Category c, String slug) {
        return products.saveAndFlush(Product.builder().category(c).name("Seed " + slug).slug(slug).active(true)
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build()).build()).getId();
    }

    @Test
    void createReturns409ProblemJsonWhenExplicitSlugDuplicateSlipsPastPreCheck() throws Exception {
        Category c = category();
        seed(c, "truffle");
        doReturn(false).when(products).existsBySlug(anyString());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\"Other\",\"slug\":\"truffle\",\"categoryId\":" + c.getId()
                                + ",\"details\":" + DETAILS + "}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void createReturns409ProblemJsonWhenDerivedSlugIsTakenBetweenLookupAndInsert() throws Exception {
        Category c = category();
        seed(c, "truffle");
        doReturn(List.of()).when(products).findSlugsWithBase(anyString());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\"Truffle\",\"categoryId\":" + c.getId() + ",\"details\":" + DETAILS + "}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateReturns409ProblemJsonWhenSlugDuplicateSlipsPastPreCheck() throws Exception {
        Category c = category();
        seed(c, "truffle");
        long other = seed(c, "sponge");
        doReturn(false).when(products).existsBySlug(anyString());
        mvc.perform(put(BASE + "/" + other).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\"Sponge\",\"slug\":\"truffle\",\"categoryId\":" + c.getId()
                                + ",\"active\":true,\"details\":" + DETAILS + "}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }
}

package com.bakeby.bakebyapi.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.bakeby.bakebyapi.catalog.entity.Variant;
import com.bakeby.bakebyapi.catalog.repository.CategoryRepository;
import com.bakeby.bakebyapi.catalog.repository.ProductRepository;
import com.bakeby.bakebyapi.catalog.repository.VariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Simulates the check-then-insert race for SKUs: the pre-check says "free" but a row exists, so the
 * unique constraint must reject the write and the API must answer 409 problem+json without internals.
 */
class VariantConstraintRaceTests extends AbstractIntegrationTest {

    private static final String PROBLEM = "application/problem+json";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @MockitoSpyBean VariantRepository variants;

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    private Product product() {
        Category c = categories.saveAndFlush(Category.builder().name("Cakes").slug("cakes").build());
        return products.saveAndFlush(Product.builder().category(c).name("Cake").slug("cake")
                .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build()).build());
    }

    private long seed(Product p, String sku) {
        return variants.saveAndFlush(Variant.builder().product(p).sku(sku).label("500 g").priceMinor(100).build())
                .getId();
    }

    @Test
    void createReturns409ProblemJsonWhenDuplicateSkuSlipsPastPreCheck() throws Exception {
        Product p = product();
        seed(p, "DUP");
        doReturn(false).when(variants).existsBySku(anyString());
        mvc.perform(post("/api/v1/admin/products/" + p.getId() + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"DUP\",\"label\":\"1 kg\",\"priceMinor\":200}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM variant", Long.class)).isEqualTo(1);
    }

    @Test
    void updateReturns409ProblemJsonWhenDuplicateSkuSlipsPastPreCheck() throws Exception {
        Product p = product();
        seed(p, "TAKEN");
        long mine = seed(p, "MINE");
        doReturn(false).when(variants).existsBySku(anyString());
        mvc.perform(put("/api/v1/admin/products/" + p.getId() + "/variants/" + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"TAKEN\",\"label\":\"1 kg\",\"priceMinor\":200,"
                                + "\"inStock\":true,\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.trace").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT sku FROM variant WHERE id = ?", String.class, mine))
                .isEqualTo("MINE");
    }
}

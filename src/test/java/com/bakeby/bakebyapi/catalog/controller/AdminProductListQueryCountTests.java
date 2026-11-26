package com.bakeby.bakebyapi.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Proves the admin product list uses a fixed number of SQL statements regardless of page size (no N+1). */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AdminProductListQueryCountTests extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/admin/products";

    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired VariantRepository variants;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;

    Statistics stats;

    @BeforeEach
    void seed() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        assertThat(stats.isStatisticsEnabled()).isTrue();
        Category cat = categories.saveAndFlush(Category.builder().name("Cakes").slug("cakes").build());
        for (int i = 0; i < 30; i++) {
            Product p = products.saveAndFlush(Product.builder().category(cat)
                    .name(String.format("Cake %02d", i)).slug("cake-" + i)
                    .details(ProductDetails.builder().dietaryType(DietaryType.VEG).build())
                    .allergens(Set.of(Allergen.GLUTEN, Allergen.DAIRY))
                    .images(List.of("https://e.com/" + i + "a.jpg", "https://e.com/" + i + "b.jpg"))
                    .build());
            for (int v = 0; v < 2; v++) {
                variants.saveAndFlush(Variant.builder().product(p).sku("SKU-" + i + "-" + v).label("v" + v)
                        .priceMinor(100).active(v == 0).build());
            }
        }
    }

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM variant");
        jdbc.execute("DELETE FROM product_image");
        jdbc.execute("DELETE FROM product_allergen");
        jdbc.execute("DELETE FROM product");
        jdbc.execute("DELETE FROM category");
    }

    /** Statements prepared while listing one full page (both pages are full, so the count query runs in both). */
    private long statementsForPage(int size, boolean includeInactive) throws Exception {
        stats.clear();
        mvc.perform(get(BASE).param("size", String.valueOf(size)).param("page", "0")
                        .param("includeInactive", String.valueOf(includeInactive)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(size)))
                .andExpect(jsonPath("$.content[0].variants", hasSize(2)))
                .andExpect(jsonPath("$.content[0].images", hasSize(2)))
                .andExpect(jsonPath("$.content[0].details.allergens", hasSize(2)))
                .andExpect(jsonPath("$.content[" + (size - 1) + "].variants", hasSize(2)));
        return stats.getPrepareStatementCount();
    }

    @Test
    void adminListStatementCountDoesNotGrowWithPageSize() throws Exception {
        long small = statementsForPage(2, true);
        long large = statementsForPage(25, true);
        assertThat(large).isEqualTo(small);
        // page select + count + images + allergens + variants
        assertThat(small).isEqualTo(5);
    }

    @Test
    void activeOnlyAdminListStatementCountDoesNotGrowWithPageSize() throws Exception {
        long small = statementsForPage(2, false);
        long large = statementsForPage(25, false);
        assertThat(large).isEqualTo(small);
        assertThat(small).isEqualTo(5);
    }

    @Test
    void getByIdUsesFixedStatementCount() throws Exception {
        long id = products.findBySlug("cake-0").orElseThrow().getId();
        stats.clear();
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isOk());
        // product + images + allergens + variants
        assertThat(stats.getPrepareStatementCount()).isEqualTo(4);
    }
}

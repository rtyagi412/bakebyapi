package com.bakeby.bakebyapi.common;

import com.bakeby.bakebyapi.catalog.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Web slice test (no database needed) proving the deliberately open security setup. */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    /** The slice now picks up AdminCategoryController, which needs its service. */
    @MockitoBean
    private CategoryService categoryService;

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void permitsUnauthenticatedGetWithoutCredentials() throws Exception {
        assertThat(status(get("/api/v1/anything"))).isNotIn(401, 403).isEqualTo(404);
    }

    @Test
    void permitsUnauthenticatedAdminGetWithoutCredentials() throws Exception {
        assertThat(status(get("/api/v1/admin/anything"))).isNotIn(401, 403);
    }

    @Test
    void permitsPostWithoutCsrfToken() throws Exception {
        assertThat(status(post("/api/v1/admin/anything").contentType("application/json").content("{}")))
                .isNotIn(401, 403);
    }

    @Test
    void permitsPutAndDeleteWithoutCsrfToken() throws Exception {
        assertThat(status(put("/api/v1/admin/anything").contentType("application/json").content("{}")))
                .isNotIn(401, 403);
        assertThat(status(delete("/api/v1/admin/anything"))).isNotIn(401, 403);
    }
}

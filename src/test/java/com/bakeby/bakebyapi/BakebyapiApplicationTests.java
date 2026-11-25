package com.bakeby.bakebyapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

class BakebyapiApplicationTests extends AbstractIntegrationTest {

	@Autowired
	private javax.sql.DataSource dataSource;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void usesTestcontainersPostgresNotLocalDatabase() throws Exception {
		// @ServiceConnection overrides the datasource via connection details, not the spring.datasource.url property,
		// so inspect the live connection.
		String url;
		try (java.sql.Connection c = dataSource.getConnection()) {
			url = c.getMetaData().getURL();
		}
		assertThat(url).startsWith("jdbc:postgresql://").doesNotContain(":5432/");
		assertThat(url).contains("/test");
		assertThat(url).isEqualTo(postgres.getJdbcUrl());
	}

	@Autowired
	private org.testcontainers.postgresql.PostgreSQLContainer postgres;

	@Test
	void recordsContainerIdForSharingCheck() {
		String id = postgres.getContainerId();
		String first = System.getProperty("bakeby.test.containerId");
		if (first == null) {
			System.setProperty("bakeby.test.containerId", id);
		}
		assertThat(id).isEqualTo(System.getProperty("bakeby.test.containerId"));
	}

	@Test
	void autowiresMockMvc() {
		assertThat(mockMvc).isNotNull();
	}

}

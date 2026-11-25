package com.bakeby.bakebyapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Loads application.yaml only (no DB, no full context) and checks the bound values. */
class ApplicationYamlConfigTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer());

	@Test
	void usesLocalDockerDefaultsForDatasource() {
		runner.run(ctx -> {
			var env = ctx.getEnvironment();
			assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/bakeby");
			assertThat(env.getProperty("spring.datasource.username")).isEqualTo("bakeby");
			assertThat(env.getProperty("spring.datasource.password")).isEqualTo("bakeby");
		});
	}

	@Test
	void overridesDatasourceFromDbEnvVariables() {
		runner.withPropertyValues("DB_URL=jdbc:postgresql://db.example:5433/other", "DB_USER=alice", "DB_PASSWORD=s3cret")
				.run(ctx -> {
					var env = ctx.getEnvironment();
					assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.example:5433/other");
					assertThat(env.getProperty("spring.datasource.username")).isEqualTo("alice");
					assertThat(env.getProperty("spring.datasource.password")).isEqualTo("s3cret");
				});
	}

	@Test
	void validatesSchemaAndEnablesFlyway() {
		runner.run(ctx -> {
			var env = ctx.getEnvironment();
			assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
			assertThat(env.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
		});
	}

	@Test
	void configuresInrCurrency() {
		runner.run(ctx -> assertThat(ctx.getEnvironment().getProperty("bakeby.currency")).isEqualTo("INR"));
	}
}

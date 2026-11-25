package com.bakeby.bakebyapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Second integration class. The extra property forces a distinct Spring context (no context-cache hit),
 * so seeing a single container id across contexts proves the static container is shared, not restarted.
 */
@TestPropertySource(properties = "bakeby.test.distinct-context=true")
class SharedContainerTests extends AbstractIntegrationTest {

	/** Container ids observed by any class in this JVM (also written by BakebyapiApplicationTests-style checks). */
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	@Autowired
	private PostgreSQLContainer postgres;

	@Test
	void reusesTheSameContainerAcrossDistinctContexts() {
		assertThat(postgres.isRunning()).isTrue();
		SEEN.add(postgres.getContainerId());
		String first = System.getProperty("bakeby.test.containerId");
		if (first == null) {
			System.setProperty("bakeby.test.containerId", postgres.getContainerId());
		}
		assertThat(postgres.getContainerId()).isEqualTo(System.getProperty("bakeby.test.containerId"));
		assertThat(SEEN).hasSize(1);
	}
}

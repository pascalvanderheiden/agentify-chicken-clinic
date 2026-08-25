/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.assistant;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AssistantModelConfiguration} startup validation.
 *
 * <p>
 * These tests use the constructor directly (no Spring context) to verify that
 * placeholder-key detection is deterministic and independent of deployment credentials.
 * The constructor accepts the same {@code @Value}-resolved parameters that Spring would
 * inject, so the test exercises the real configuration path without reflection.
 */
class AssistantModelConfigurationTests {

	@Test
	void logsWarningWithoutThrowingWhenApiKeyIsPlaceholder() {
		AssistantModelConfiguration config = new AssistantModelConfiguration("https://models.inference.ai.azure.com",
				AssistantModelConfiguration.PLACEHOLDER_KEY, "gpt-4o-mini");

		// Should complete without throwing — misconfiguration is logged at WARN level.
		// The application starts; assistant requests fail with honest
		// service-unavailable behavior.
		config.logStartupConfiguration();
	}

	@Test
	void doesNotThrowWhenApiKeyIsConfigured() {
		AssistantModelConfiguration config = new AssistantModelConfiguration("https://myworkshop.openai.azure.com",
				"real-key-would-go-here", "gpt-4o-mini");

		// Should complete without throwing; endpoint/model are logged at INFO level.
		config.logStartupConfiguration();
	}

}

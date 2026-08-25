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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AssistantModelConfiguration} startup validation.
 *
 * <p>
 * These tests use the class directly (no Spring context) to verify that placeholder-key
 * detection is deterministic and independent of deployment credentials.
 */
class AssistantModelConfigurationTests {

	@Test
	void failsFastWhenApiKeyIsPlaceholder() {
		AssistantModelConfiguration config = new AssistantModelConfiguration();
		setField(config, "apiKey", AssistantModelConfiguration.PLACEHOLDER_KEY);
		setField(config, "baseUrl", "https://models.inference.ai.azure.com");
		setField(config, "model", "gpt-4o-mini");

		assertThatThrownBy(config::logStartupConfiguration).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("AZURE_OPENAI_API_KEY");
	}

	@Test
	void doesNotThrowWhenApiKeyIsConfigured() {
		AssistantModelConfiguration config = new AssistantModelConfiguration();
		setField(config, "apiKey", "real-key-would-go-here");
		setField(config, "baseUrl", "https://myworkshop.openai.azure.com");
		setField(config, "model", "gpt-4o-mini");

		// Should complete without throwing; endpoint/model are logged at INFO level.
		config.logStartupConfiguration();
	}

	private static void setField(Object target, String fieldName, String value) {
		try {
			java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
			f.setAccessible(true);
			f.set(target, value);
		}
		catch (ReflectiveOperationException ex) {
			throw new RuntimeException(ex);
		}
	}

}

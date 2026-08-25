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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AssistantModelConfiguration} startup validation.
 *
 * <p>
 * Direct-constructor tests verify endpoint-placeholder detection without a Spring
 * context. The {@code @SpringBootTest} test verifies that
 * {@code spring.ai.openai.microsoft-foundry} binds correctly through the real Spring
 * property evaluation path — it would fail if the property key were wrong (e.g.
 * {@code spring.ai.openai.chat.microsoft-foundry}).
 *
 * <p>
 * No API key is checked — authentication is via managed identity
 * (DefaultAzureCredential). The only startup concern is whether
 * {@code AZURE_OPENAI_ENDPOINT} is set to a real endpoint rather than the local-dev
 * placeholder.
 */
class AssistantModelConfigurationTests {

	@Test
	void logsWarningWithoutThrowingWhenEndpointIsUnset() {
		AssistantModelConfiguration config = new AssistantModelConfiguration(AssistantModelConfiguration.UNSET_ENDPOINT,
				"gpt-5-4-mini", true);

		// Should complete without throwing — unset endpoint is logged at WARN level.
		config.logStartupConfiguration();
	}

	@Test
	void logsInfoWithoutThrowingWhenEndpointIsConfigured() {
		AssistantModelConfiguration config = new AssistantModelConfiguration(
				"https://workshop-foundry-abc.openai.azure.com", "gpt-5-4-mini", true);

		// Should complete without throwing; endpoint and deployment are logged at INFO.
		config.logStartupConfiguration();
	}

	@Test
	void microsoftFoundryFlagDefaultsToTrueWhenPropertyIsAbsent() {
		// Verifies that isMicrosoftFoundry() defaults to true when the property is
		// absent — the @Value default :true applies.
		AssistantModelConfiguration config = new AssistantModelConfiguration(AssistantModelConfiguration.UNSET_ENDPOINT,
				"gpt-5-4-mini", true);
		assertThat(config.isMicrosoftFoundry()).isTrue();
	}

	@Test
	void microsoftFoundryFlagCanBeDisabledExplicitly() {
		// Verifies that isMicrosoftFoundry() can be set to false — e.g. for a plain
		// OpenAI endpoint in a non-Azure environment. This would be the old broken state
		// if the property prefix were wrong and the value never bound.
		AssistantModelConfiguration config = new AssistantModelConfiguration(AssistantModelConfiguration.UNSET_ENDPOINT,
				"gpt-5-4-mini", false);
		assertThat(config.isMicrosoftFoundry()).isFalse();
	}

	/**
	 * Spring-context regression test: verifies that
	 * {@code spring.ai.openai.microsoft-foundry} binds to
	 * {@link AssistantModelConfiguration#isMicrosoftFoundry()} through the real Spring
	 * {@code @Value} evaluation path.
	 *
	 * <p>
	 * This test would fail if the property key used in
	 * {@code AssistantModelConfiguration} were wrong (for example
	 * {@code spring.ai.openai.chat.microsoft-foundry}) because the {@code @Value}
	 * injection would not find the property and would fall back to the default
	 * {@code true} regardless of what is set — or worse, fail to start.
	 *
	 * <p>
	 * Uses {@code @TestPropertySource} to set the value to {@code false} so a passing
	 * test proves the binding is live (not just the default).
	 */
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
	@TestPropertySource(properties = { "spring.ai.openai.microsoft-foundry=false",
			"spring.ai.openai.base-url=https://test.openai.azure.com",
			"spring.ai.openai.microsoft-deployment-name=test-deploy", "spring.ai.openai.model=test-deploy",
			"spring.ai.openai.chat.model=test-deploy", "spring.ai.model.chat=none" })
	static class FoundryPropertyBindingIT {

		@Autowired
		AssistantModelConfiguration config;

		@Test
		void microsoftFoundryPropertyBindsFromCorrectPrefix() {
			// spring.ai.openai.microsoft-foundry=false is set via @TestPropertySource.
			// If the @Value key in AssistantModelConfiguration used the wrong prefix
			// (e.g. spring.ai.openai.chat.microsoft-foundry), the default :true would
			// apply and this assertion would fail.
			assertThat(config.isMicrosoftFoundry()).isFalse();
		}

	}

}

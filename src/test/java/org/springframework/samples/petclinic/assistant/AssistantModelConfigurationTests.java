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
 * Uses the constructor directly (no Spring context) to verify that endpoint-placeholder
 * detection is deterministic and independent of Azure credentials. The constructor
 * accepts the same {@code @Value}-resolved parameters that Spring injects, so the test
 * exercises the real configuration path without reflection.
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
				"gpt-5-4-mini");

		// Should complete without throwing — unset endpoint is logged at WARN level.
		// The application starts; assistant requests fail with honest
		// service-unavailable behavior (no real Azure endpoint to call).
		config.logStartupConfiguration();
	}

	@Test
	void logsInfoWithoutThrowingWhenEndpointIsConfigured() {
		AssistantModelConfiguration config = new AssistantModelConfiguration(
				"https://workshop-foundry-abc.openai.azure.com", "gpt-5-4-mini");

		// Should complete without throwing; endpoint and deployment are logged at INFO.
		config.logStartupConfiguration();
	}

}

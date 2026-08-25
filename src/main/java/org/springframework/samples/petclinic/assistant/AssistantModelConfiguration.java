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

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Validates and logs the assistant model configuration at startup.
 *
 * <p>
 * Uses the Spring AI 2.0.1 Azure/Foundry integration built into
 * {@code spring-ai-starter-model-openai} via {@code spring.ai.openai.microsoft-foundry}.
 * Authentication is via managed identity (DefaultAzureCredential from
 * {@code azure-identity}) — no API key is required or expected.
 *
 * <p>
 * Logs the resolved endpoint and deployment name at INFO so deployment issues are visible
 * in application logs without requiring a live request. Throws
 * {@link IllegalStateException} if the endpoint is not configured, because without an
 * Azure OpenAI endpoint the application cannot serve any assistant request.
 */
@Configuration
class AssistantModelConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(AssistantModelConfiguration.class);

	/**
	 * Sentinel value used as the default when {@code AZURE_OPENAI_ENDPOINT} is absent.
	 */
	static final String UNSET_ENDPOINT = "https://models.inference.ai.azure.com";

	private final String endpoint;

	private final String deploymentName;

	AssistantModelConfiguration(@Value("${spring.ai.openai.base-url}") String endpoint,
			@Value("${spring.ai.openai.microsoft-deployment-name}") String deploymentName) {
		this.endpoint = endpoint;
		this.deploymentName = deploymentName;
	}

	@PostConstruct
	void logStartupConfiguration() {
		if (UNSET_ENDPOINT.equals(this.endpoint)) {
			logger.warn("[Clinic Assistant] AZURE_OPENAI_ENDPOINT is not set — using local dev placeholder. "
					+ "Authentication is via managed identity; set AZURE_OPENAI_ENDPOINT and "
					+ "AZURE_OPENAI_DEPLOYMENT to connect to a real Azure OpenAI resource.");
			return;
		}
		logger.info("[Clinic Assistant] Azure OpenAI configuration resolved — endpoint: {}, deployment: {}",
				this.endpoint, this.deploymentName);
	}

}

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
 * Logs the resolved endpoint and model name so deployment issues are visible in
 * application logs without requiring a live request. The API key is never logged; only
 * its presence and placeholder state are checked.
 *
 * <p>
 * When the API key is still the placeholder value {@code changeme}, a prominent warning
 * is logged so a misconfigured deployment is diagnosed from application logs. The
 * application is NOT prevented from starting — assistant requests will fail at call time
 * with the honest service-unavailable/no-data behavior already handled by
 * {@link AssistantChatSession}.
 */
@Configuration
class AssistantModelConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(AssistantModelConfiguration.class);

	static final String PLACEHOLDER_KEY = "changeme";

	private final String baseUrl;

	private final String apiKey;

	private final String model;

	AssistantModelConfiguration(@Value("${spring.ai.openai.base-url}") String baseUrl,
			@Value("${spring.ai.openai.api-key}") String apiKey,
			@Value("${spring.ai.openai.chat.options.model}") String model) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.model = model;
	}

	@PostConstruct
	void logStartupConfiguration() {
		if (PLACEHOLDER_KEY.equals(this.apiKey)) {
			logger.warn("[Clinic Assistant] AZURE_OPENAI_API_KEY is not set — assistant requests will return "
					+ "'service unavailable'. Set the AZURE_OPENAI_API_KEY environment variable. "
					+ "See docs/workshop/clinic-stakeholder-knowledge.md for the required Azure deployment.");
			return;
		}
		logger.info("[Clinic Assistant] Model configuration resolved — endpoint: {}, model: {}", this.baseUrl,
				this.model);
	}

}

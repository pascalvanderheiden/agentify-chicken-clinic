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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the chat boundary system prompt text.
 */
class SystemPromptTests {

	@Test
	void systemPromptDeclinesMutationRequests() {
		assertThat(SystemPrompt.TEXT).contains("cannot make changes to clinic data");
	}

	@Test
	void systemPromptDeclinesVeterinaryAdvice() {
		assertThat(SystemPrompt.TEXT).contains("not able to provide veterinary diagnosis or treatment advice");
	}

	@Test
	void systemPromptDeclinesOutOfScopeRequests() {
		assertThat(SystemPrompt.TEXT).contains("I can only help with PetClinic records");
	}

	@Test
	void systemPromptRequiresAdmittingAbsentRecords() {
		assertThat(SystemPrompt.TEXT).contains("ABSENT RESULTS");
		assertThat(SystemPrompt.TEXT).contains("no matching record was found");
	}

	@Test
	void systemPromptHandlesAmbiguityWithCappedCandidatesAndNarrowingQuestion() {
		assertThat(SystemPrompt.TEXT).contains("AMBIGUOUS RESULTS");
		assertThat(SystemPrompt.TEXT).contains("five candidates");
		assertThat(SystemPrompt.TEXT).contains("narrowing question");
	}

	@Test
	void systemPromptForbidsIdentitySelection() {
		assertThat(SystemPrompt.TEXT).containsIgnoringCase("do not select an identity");
		assertThat(SystemPrompt.TEXT).contains("Never fabricate");
	}

}

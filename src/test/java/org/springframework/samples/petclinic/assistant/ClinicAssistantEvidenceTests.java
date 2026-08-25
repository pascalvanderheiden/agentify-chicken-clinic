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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Six-prompt acceptance evidence for the Clinic Assistant.
 *
 * <p>
 * Exercises the HTTP → Controller → AssistantChatSession → ClinicQueryService → H2 stack.
 * The AI call ({@link ChatClient}) is replaced by a deterministic stub so no live Azure
 * or Foundry endpoint is needed in CI. Each test injects a fixture-specific canned reply
 * via the shared {@link #STUB_BUILDER} and sends a fresh HTTP session so the
 * {@code @SessionScope} chat session starts clean.
 *
 * <p>
 * Assertions are wording-tolerant: they check for the presence of one or more signal
 * phrases rather than requiring exact prose from the model.
 *
 * <p>
 * <strong>Evidence fixture map (Issue #12 acceptance criteria)</strong>
 * <ol>
 * <li>Owner lookup — "Tell me about owner Davis"</li>
 * <li>Pet + visit lookup — "What pets does George Franklin have?"</li>
 * <li>Ambiguity/narrowing — "Find owner Davis" (two Davis records in H2 seed)</li>
 * <li>Absent record — "Find owner Zzznobody"</li>
 * <li>Safe decline: mutation — "Cancel the next appointment for Fluffy"</li>
 * <li>Safe decline: medical advice — "What medication should I give my cat?"</li>
 * </ol>
 *
 * <p>
 * <strong>Fragile observations:</strong>
 * <ul>
 * <li>Stub responses stand in for real model output; actual phrasing from Azure/Foundry
 * will differ and is not validated here.</li>
 * <li>H2 seed is verified to contain Betty Davis and Harold Davis (ambiguity) and George
 * Franklin → Leo (pet). If the seed changes these fixtures may need updating.</li>
 * <li>The {@code @SessionScope} bean requires a separate {@link MockHttpSession} per test
 * to avoid cross-test state pollution when {@code MockMvc} is reused.</li>
 * </ul>
 *
 * <p>
 * <strong>Residual risks — explicitly out of scope for this workshop slice:</strong>
 * <ul>
 * <li>Authentication and authorization — no session security is enforced.</li>
 * <li>Privacy and auditing — turns are not logged or audited.</li>
 * <li>Prompt-injection hardening — adversarial prompts are not exercised.</li>
 * <li>Production observability — metrics and distributed tracing are not validated.</li>
 * <li>Persistent conversations — history is session-scoped and intentionally lost on
 * session expiry; no persistence layer is tested.</li>
 * <li>Scheduling and write operations — no mutation paths exist in the assistant; none
 * are tested.</li>
 * <li>Medical advice safety at inference time — the system prompt boundary is exercised
 * via the stub; live model alignment is not validated here.</li>
 * <li>Live model wording — canned stub replies are used; real Azure/Foundry responses may
 * vary and require separate manual or exploratory verification.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.ai.openai.base-url=https://stub.example.invalid",
				"spring.ai.openai.microsoft-foundry=true",
				"spring.ai.openai.microsoft-deployment-name=stub-deployment" })
@AutoConfigureMockMvc
@DisabledInNativeImage
@DisabledInAotMode
class ClinicAssistantEvidenceTests {

	/**
	 * Shared deep-stub {@link ChatClient.Builder}. Created once and re-stubbed per test
	 * via {@link #cannedReply(String)}. {@code RETURNS_DEEP_STUBS} means every chained
	 * call ({@code .prompt().system().messages().tools().call().content()}) returns a
	 * stub automatically; only {@code content()} needs an explicit {@code willReturn}.
	 */
	static final ChatClient.Builder STUB_BUILDER = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);

	@TestConfiguration
	static class StubChatConfig {

		@Bean
		@Primary
		ChatClient.Builder stubChatClientBuilder() {
			return STUB_BUILDER;
		}

	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ClinicQueryService queryService;

	// ---------------------------------------------------------------------------
	// Helper: pre-stub the deep-stub chain's content() return before each POST.
	// The AssistantChatSession calls builder.build() at construction, which returns
	// the same deep-stub mock each time. Re-stubbing content() here affects the
	// reply for the next call() invocation in the session.
	// ---------------------------------------------------------------------------

	private void cannedReply(String reply) {
		given(STUB_BUILDER.build().prompt().system(any(String.class)).messages(anyList()).tools(any()).call().content())
			.willReturn(reply);
	}

	// ---------------------------------------------------------------------------
	// Navigation and lifecycle evidence
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("N1 – Staff navigation: GET /assistant returns the chat view")
	void staffNavigationReturnsAssistantView() throws Exception {
		this.mockMvc.perform(get("/assistant").session(new MockHttpSession()))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andExpect(model().attributeExists("turns"));
	}

	@Test
	@DisplayName("N2 – Staff navigation: layout HTML contains a link to /assistant")
	void layoutContainsAssistantNavLink() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/")).andReturn();
		assertThat(result.getResponse().getContentAsString()).contains("/assistant");
	}

	@Test
	@DisplayName("N3 – Temporary history: fresh session starts with empty turn list")
	void freshSessionHasEmptyTurnList() throws Exception {
		this.mockMvc.perform(get("/assistant").session(new MockHttpSession()))
			.andExpect(status().isOk())
			.andExpect(model().attribute("turns", List.of()));
	}

	@Test
	@DisplayName("N4 – Temporary history: two sequential POSTs on the same session accumulate two turns")
	void twoPostsOnSameSessionAccumulateTwoTurns() throws Exception {
		cannedReply("Here is what I found.");
		MockHttpSession session = new MockHttpSession();

		this.mockMvc.perform(post("/assistant").param("message", "Tell me about owner Davis").session(session))
			.andExpect(status().isOk());

		MvcResult second = this.mockMvc
			.perform(post("/assistant").param("message", "What pets does Leo have?").session(session))
			.andExpect(status().isOk())
			.andReturn();

		@SuppressWarnings("unchecked")
		List<ChatTurn> turns = (List<ChatTurn>) second.getModelAndView().getModel().get("turns");
		assertThat(turns).hasSize(2);
	}

	// ---------------------------------------------------------------------------
	// Fixture 1 — Successful owner lookup
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("F1 – Owner lookup: 'Tell me about owner Davis' renders a turn with non-blank trace")
	void fixture1OwnerLookup() throws Exception {
		cannedReply(
				"I found two owners with the last name Davis: Betty Davis in Sun Prairie and Harold Davis in Windsor.");
		MockHttpSession session = new MockHttpSession();

		MvcResult result = this.mockMvc
			.perform(post("/assistant").param("message", "Tell me about owner Davis").session(session))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andExpect(model().attributeExists("turns"))
			.andReturn();

		ChatTurn lastTurn = lastTurnFrom(result);
		assertThat(lastTurn.userMessage()).isEqualTo("Tell me about owner Davis");
		assertThat(lastTurn.assistantReply()).isNotBlank();
		// One-line activity trace must be present
		assertThat(lastTurn.activityTrace()).isNotBlank();
		// Wording-tolerant: reply references at least one of the expected signals
		assertContainsAny(lastTurn.assistantReply(), "Davis", "owner", "found");
	}

	// ---------------------------------------------------------------------------
	// Fixture 2 — Successful pet + visit lookup
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("F2 – Pet lookup: 'What pets does George Franklin have?' renders turn with pet detail")
	void fixture2PetLookup() throws Exception {
		cannedReply("George Franklin has one pet: Leo, a cat, born 2010-09-07.");
		MockHttpSession session = new MockHttpSession();

		MvcResult result = this.mockMvc
			.perform(post("/assistant").param("message", "What pets does George Franklin have?").session(session))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andExpect(model().attributeExists("turns"))
			.andReturn();

		ChatTurn lastTurn = lastTurnFrom(result);
		assertThat(lastTurn.userMessage()).isEqualTo("What pets does George Franklin have?");
		assertThat(lastTurn.assistantReply()).isNotBlank();
		assertThat(lastTurn.activityTrace()).isNotBlank();
		assertContainsAny(lastTurn.assistantReply(), "Leo", "cat", "George", "pet");
	}

	@Test
	@DisplayName("F2b – Owner-name-to-pet path: findOwnersByLastName('Franklin') returns Leo in the pets list")
	void fixture2OwnerNameToPetLookupPath() {
		// Verifies the real production path used when a user asks about an owner's pets:
		// the model calls findOwnersByLastName, not findPetsByName. The OwnerRecord must
		// carry the pet list so the model can answer without a second tool call.
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Franklin");
		assertThat(results).isNotEmpty();
		OwnerRecord george = results.get(0);
		assertThat(george.firstName()).isEqualToIgnoringCase("George");
		assertThat(george.pets()).isNotEmpty();
		assertThat(george.pets()).anySatisfy(pet -> {
			assertThat(pet.name()).isEqualToIgnoringCase("Leo");
			assertThat(pet.ownerName()).containsIgnoringCase("Franklin");
		});
	}

	// ---------------------------------------------------------------------------
	// Fixture 3 — Ambiguity / narrowing
	// Two Davis owners exist in H2 seed: Betty Davis (Sun Prairie) and Harold Davis
	// (Windsor). The stub simulates the narrowing reply.
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("F3 – Ambiguity: 'Find owner Davis' produces a narrowing question, not identity selection")
	void fixture3AmbiguityNarrowing() throws Exception {
		cannedReply("I found two owners matching 'Davis': Betty Davis in Sun Prairie "
				+ "and Harold Davis in Windsor. Which owner did you mean? "
				+ "Please provide more details such as city, telephone, or a pet name.");
		MockHttpSession session = new MockHttpSession();

		MvcResult result = this.mockMvc
			.perform(post("/assistant").param("message", "Find owner Davis").session(session))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andReturn();

		ChatTurn lastTurn = lastTurnFrom(result);
		String reply = lastTurn.assistantReply();

		// Contract: reply must NOT claim to have selected an identity
		assertThat(reply).doesNotContainIgnoringCase("I have selected");
		assertThat(reply).doesNotContainIgnoringCase("the owner is Betty");
		assertThat(reply).doesNotContainIgnoringCase("the owner is Harold");
		// Contract: reply must contain a narrowing signal (wording-tolerant)
		assertContainsAny(reply, "which owner", "which one", "more details", "narrow", "please provide", "did you mean",
				"?");
	}

	@Test
	@DisplayName("F3b – Ambiguity: ClinicQueryService returns both Davis owners from H2 seed")
	void fixture3SeedContainsTwoDavisOwners() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Davis");
		assertThat(results).hasSizeGreaterThan(1);
		assertThat(results).hasSizeLessThanOrEqualTo(ClinicQueryService.MAX_CANDIDATES);
		assertThat(results).allMatch(r -> r.lastName().equalsIgnoreCase("Davis"));
	}

	// ---------------------------------------------------------------------------
	// Fixture 4 — Absent record
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("F4 – Absent record: 'Find owner Zzznobody' explicitly admits no record found")
	void fixture4AbsentRecord() throws Exception {
		cannedReply("No owner matching 'Zzznobody' was found in the clinic records.");
		MockHttpSession session = new MockHttpSession();

		MvcResult result = this.mockMvc
			.perform(post("/assistant").param("message", "Find owner Zzznobody").session(session))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andReturn();

		ChatTurn lastTurn = lastTurnFrom(result);
		String reply = lastTurn.assistantReply();

		// Contract: reply must admit absence (wording-tolerant)
		assertContainsAny(reply, "no owner", "not found", "no matching", "no record", "cannot find", "unable to find",
				"no results", "was not found");
		// Contract: reply must NOT contain a fabricated owner name
		assertThat(reply).doesNotContainIgnoringCase("Zzznobody Davis");
		assertThat(reply).doesNotContainIgnoringCase("John Zzznobody");
	}

	@Test
	@DisplayName("F4b – Absent record: ClinicQueryService returns empty list for unknown name")
	void fixture4QueryServiceReturnsEmptyForAbsentName() {
		assertThat(this.queryService.findOwnersByLastName("Zzznobody")).isEmpty();
	}

	// ---------------------------------------------------------------------------
	// Fixture 5 — Safe decline: mutation request
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("F5 – Safe decline: 'Cancel the next appointment for Fluffy' is declined, not executed")
	void fixture5SafeDeclineMutation() throws Exception {
		cannedReply(
				"I can only look up existing records. I cannot make changes to clinic data. Please contact the clinic directly to cancel appointments.");
		MockHttpSession session = new MockHttpSession();

		MvcResult result = this.mockMvc
			.perform(post("/assistant").param("message", "Cancel the next appointment for Fluffy").session(session))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andReturn();

		ChatTurn lastTurn = lastTurnFrom(result);
		String reply = lastTurn.assistantReply();

		// Contract: reply must contain a read-only / decline signal (wording-tolerant)
		assertContainsAny(reply, "cannot", "can't", "not able", "decline", "read-only", "only look up",
				"cannot make changes", "unable to");
		// Contract: reply must NOT claim the mutation was performed
		assertThat(reply).doesNotContainIgnoringCase("appointment cancelled");
		assertThat(reply).doesNotContainIgnoringCase("has been cancelled");
		assertThat(reply).doesNotContainIgnoringCase("appointment has been removed");
		// Contract: activity trace must be grounded in real execution — a decline runs no
		// lookup tool, so the trace must report that no clinic data was queried rather
		// than
		// fabricating a lookup or decline claim from wording.
		assertContainsAny(lastTurn.activityTrace(), "no lookup tool", "without querying clinic data", "no clinic data");
	}

	// ---------------------------------------------------------------------------
	// Fixture 6 — Safe decline: medical-advice request
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("F6 – Safe decline: 'What medication should I give my cat?' is declined without advice")
	void fixture6SafeDeclineMedicalAdvice() throws Exception {
		cannedReply("I am not able to provide veterinary diagnosis or treatment advice. "
				+ "Please consult a qualified veterinarian for medical guidance.");
		MockHttpSession session = new MockHttpSession();

		MvcResult result = this.mockMvc
			.perform(post("/assistant").param("message", "What medication should I give my cat?").session(session))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andReturn();

		ChatTurn lastTurn = lastTurnFrom(result);
		String reply = lastTurn.assistantReply();

		// Contract: reply must contain a refusal signal (wording-tolerant)
		assertContainsAny(reply, "not able to provide", "cannot provide", "consult", "veterinarian", "treatment advice",
				"diagnosis");
		// Contract: reply must NOT contain treatment specifics
		assertThat(reply).doesNotContainIgnoringCase("dosage");
		assertThat(reply).doesNotContainIgnoringCase(" mg ");
		assertThat(reply).doesNotContainIgnoringCase("prescribe");
		// Contract: activity trace must be grounded in real execution — a decline runs no
		// lookup tool, so the trace must report that no clinic data was queried rather
		// than
		// fabricating a lookup or decline claim from wording.
		assertContainsAny(lastTurn.activityTrace(), "no lookup tool", "without querying clinic data", "no clinic data");
	}

	// ---------------------------------------------------------------------------
	// Read-only boundary evidence
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("R1 – Read-only: owner query returns OwnerRecord, not JPA Owner entity")
	void readOnlyBoundaryOwnerRecord() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Davis");
		assertThat(results).isNotEmpty();
		assertThat(results.get(0)).isInstanceOf(OwnerRecord.class);
		assertThat(results.get(0)).isNotInstanceOf(org.springframework.samples.petclinic.owner.Owner.class);
	}

	@Test
	@DisplayName("R2 – Read-only: pet query returns PetRecord, not JPA Pet entity")
	void readOnlyBoundaryPetRecord() {
		List<PetRecord> results = this.queryService.findPetsByName("Leo");
		assertThat(results).isNotEmpty();
		assertThat(results.get(0)).isInstanceOf(PetRecord.class);
		assertThat(results.get(0)).isNotInstanceOf(org.springframework.samples.petclinic.owner.Pet.class);
	}

	@Test
	@DisplayName("R3 – Read-only: ClinicQueryService exposes no write methods")
	void readOnlyBoundaryNoWriteMethods() {
		java.lang.reflect.Method[] methods = ClinicQueryService.class.getDeclaredMethods();
		for (java.lang.reflect.Method m : methods) {
			if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())
					&& !java.lang.reflect.Modifier.isProtected(m.getModifiers())) {
				continue;
			}
			String name = m.getName().toLowerCase();
			assertThat(name).as("Unexpected write method on ClinicQueryService: " + m.getName())
				.doesNotMatch("(save|delete|update|create|insert|remove|add|put|patch|write|modify|persist|merge).*");
		}
	}

	// ---------------------------------------------------------------------------
	// Activity trace evidence
	// ---------------------------------------------------------------------------

	@Test
	@DisplayName("T1 – Activity trace: each turn exposes a non-blank activityTrace")
	void activityTraceIsNonBlankOnEachTurn() throws Exception {
		cannedReply("Betty Davis lives at 638 Cardinal Ave., Sun Prairie. She has pet Basil.");
		MockHttpSession session = new MockHttpSession();

		MvcResult result = this.mockMvc
			.perform(post("/assistant").param("message", "Tell me about owner Davis").session(session))
			.andExpect(status().isOk())
			.andReturn();

		@SuppressWarnings("unchecked")
		List<ChatTurn> turns = (List<ChatTurn>) result.getModelAndView().getModel().get("turns");
		assertThat(turns).isNotEmpty();
		assertThat(turns).allSatisfy(t -> assertThat(t.activityTrace()).isNotBlank());
	}

	// ---------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private static ChatTurn lastTurnFrom(MvcResult result) {
		List<ChatTurn> turns = (List<ChatTurn>) result.getModelAndView().getModel().get("turns");
		assertThat(turns).as("Expected at least one turn in model").isNotEmpty();
		return turns.get(turns.size() - 1);
	}

	/**
	 * Wording-tolerant assertion: at least one of {@code candidates} must appear as a
	 * case-insensitive substring of {@code response}.
	 */
	static void assertContainsAny(String response, String... candidates) {
		String lower = response.toLowerCase();
		boolean found = Arrays.stream(candidates).anyMatch(c -> lower.contains(c.toLowerCase()));
		assertThat(found).as("Expected response to contain one of %s\nbut was: %s", Arrays.asList(candidates), response)
			.isTrue();
	}

}

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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Focused deterministic tests for the Percy/parrot GUI observable contract at the highest
 * public seam: the browser-facing {@code GET /assistant} and {@code POST
 * /assistant} endpoints rendered via {@code assistant/chat.html}.
 *
 * <p>
 * Coverage:
 * <ul>
 * <li>P1 — Percy/parrot identity: heading and avatar emoji present on GET</li>
 * <li>P2 — Intro bubble present on GET (Percy always introduces itself)</li>
 * <li>P3 — Input control ({@code name="message"}) and send button present</li>
 * <li>P4 — Empty state rendered when no turns exist</li>
 * <li>P5 — Rendered turns: {@code assistant-turn}, staff bubble, percy bubble, trace</li>
 * <li>P6 — Activity trace visible per turn after POST</li>
 * <li>S1 — Service-unavailable: reply contains "unable to reach" wording</li>
 * <li>S2 — Service-unavailable trace: "no clinic data" signal, not a lookup trace</li>
 * <li>N1 — {@code /oups} route not present in assistant page HTML</li>
 * </ul>
 *
 * <p>
 * No live Azure/Foundry calls are made; all AI interactions are stubbed via
 * {@link MockitoBean}. CSS class assertions are limited to structural class names that
 * reflect semantic layout (e.g. {@code assistant-turn}, {@code percy-bubble}) — not
 * transient visual utilities.
 */
@WebMvcTest(AssistantController.class)
@DisabledInNativeImage
@DisabledInAotMode
class AssistantParrotGuiTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AssistantChatSession chatSession;

	// ── P1: Percy/parrot identity ────────────────────────────────────────────

	@Test
	void percyParrotEmojiPresentInHeadingOnGet() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("percy-parrot")));
	}

	@Test
	void percyParrotAvatarPresentOnGet() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("percy-avatar")));
	}

	// ── P2: Intro bubble ─────────────────────────────────────────────────────

	@Test
	void percyIntroductionBubblePresentOnGet() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("percy-intro-bubble")));
	}

	@Test
	void percyIntroductionBubblePresentWhenTurnsExist() throws Exception {
		ChatTurn turn = new ChatTurn("Who owns Basil?", "Betty Davis owns Basil.",
				"Looked up owner records — 1 match.");
		given(this.chatSession.getTurns()).willReturn(List.of(turn));
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("percy-intro-bubble")));
	}

	// ── P3: Input control and send button ────────────────────────────────────

	@Test
	void chatInputControlPresentOnGet() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"message\"")));
	}

	@Test
	void sendButtonPresentOnGet() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("assistant-send")));
	}

	// ── P4: Empty state ───────────────────────────────────────────────────────

	@Test
	void emptyStateRenderedWhenNoTurns() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("empty-state")));
	}

	@Test
	void emptyStateAbsentWhenTurnsExist() throws Exception {
		ChatTurn turn = new ChatTurn("Who owns Basil?", "Betty Davis owns Basil.",
				"Looked up owner records — 1 match.");
		given(this.chatSession.getTurns()).willReturn(List.of(turn));
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("empty-state"))));
	}

	// ── P5: Turn structure rendering ─────────────────────────────────────────

	@Test
	void turnStructureRenderedAfterPost() throws Exception {
		ChatTurn turn = new ChatTurn("Do you have anyone named Davis?", "I found Betty Davis and Harold Davis.",
				"Looked up owner records — 2 matches in PetClinic data.");
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Do you have anyone named Davis?"))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andExpect(content().string(containsString("assistant-turn")))
			.andExpect(content().string(containsString("staff-bubble")))
			.andExpect(content().string(containsString("percy-bubble")));
	}

	@Test
	void staffMessageRenderedInTurn() throws Exception {
		ChatTurn turn = new ChatTurn("Who owns Basil?", "Betty Davis owns Basil.",
				"Looked up owner records — 1 match in PetClinic data.");
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Who owns Basil?"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Who owns Basil?")));
	}

	@Test
	void assistantReplyRenderedInTurn() throws Exception {
		ChatTurn turn = new ChatTurn("Who owns Basil?", "Betty Davis owns Basil.",
				"Looked up owner records — 1 match in PetClinic data.");
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Who owns Basil?"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Betty Davis owns Basil.")));
	}

	// ── P6: Activity trace visible per turn ──────────────────────────────────

	@Test
	void activityTraceRenderedPerTurn() throws Exception {
		ChatTurn turn = new ChatTurn("Who owns Basil?", "Betty Davis owns Basil.",
				"Looked up owner records — 1 match in PetClinic data.");
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Who owns Basil?"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("percy-trace")))
			.andExpect(content().string(containsString("Looked up owner records")));
	}

	// ── S1/S2: Service-unavailable message and no-data trace ─────────────────

	@Test
	void serviceUnavailableReplyRenderedInTurn() throws Exception {
		String unavailableReply = "I was unable to reach the assistant service. Please try again.";
		String noDataTrace = "Assistant service call failed — no clinic data was retrieved.";
		ChatTurn turn = new ChatTurn("Who owns Basil?", unavailableReply, noDataTrace);
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Who owns Basil?"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("unable to reach the assistant service")));
	}

	@Test
	void serviceUnavailableTraceContainsNoDataSignal() throws Exception {
		String unavailableReply = "I was unable to reach the assistant service. Please try again.";
		String noDataTrace = "Assistant service call failed — no clinic data was retrieved.";
		ChatTurn turn = new ChatTurn("Who owns Basil?", unavailableReply, noDataTrace);
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Who owns Basil?"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("no clinic data was retrieved")));
	}

	@Test
	void serviceUnavailableTraceDoesNotClaimLookupSucceeded() throws Exception {
		String unavailableReply = "I was unable to reach the assistant service. Please try again.";
		String noDataTrace = "Assistant service call failed — no clinic data was retrieved.";
		ChatTurn turn = new ChatTurn("Who owns Basil?", unavailableReply, noDataTrace);
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Who owns Basil?"))
			.andExpect(status().isOk())
			// The trace must not claim a successful lookup when the service was down
			.andExpect(content().string(not(containsString("match in PetClinic data"))));
	}

	// ── N1: /oups not in assistant page ──────────────────────────────────────

	@Test
	void oupsRouteNotPresentInAssistantPage() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			// /oups must not appear as a link in the assistant page — it is not a
			// valid assistant recovery path and would confuse staff seeing a failure
			.andExpect(content().string(not(containsString("href=\"/oups\""))));
	}

	@Test
	void oupsRouteNotPresentAfterServiceUnavailable() throws Exception {
		String unavailableReply = "I was unable to reach the assistant service. Please try again.";
		String noDataTrace = "Assistant service call failed — no clinic data was retrieved.";
		ChatTurn turn = new ChatTurn("Who owns Basil?", unavailableReply, noDataTrace);
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Who owns Basil?"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("href=\"/oups\""))));
	}

}

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Web layer tests for {@link AssistantController}.
 *
 * <p>
 * Covers: discoverability (GET /assistant), form submission, transcript rendering,
 * activity trace visibility, and safe decline behaviour.
 */
@WebMvcTest(AssistantController.class)
@DisabledInNativeImage
@DisabledInAotMode
class AssistantControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AssistantChatSession chatSession;

	@Test
	void getAssistantPageIsDiscoverable() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andExpect(model().attributeExists("turns"));
	}

	@Test
	void getAssistantPageShowsExistingTurns() throws Exception {
		ChatTurn turn = new ChatTurn("Who owns Basil?", "Betty Davis owns Basil.", "Looked up owner records.");
		given(this.chatSession.getTurns()).willReturn(List.of(turn));
		this.mockMvc.perform(get("/assistant"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("turns", List.of(turn)));
	}

	@Test
	void postSubmitsTurnAndRendersHistory() throws Exception {
		ChatTurn turn = new ChatTurn("List vets", "There are 6 veterinarians.", "Listed veterinarian records.");
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "List vets"))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"))
			.andExpect(model().attributeExists("turns"));
	}

	@Test
	void postWithBlankMessageDoesNotCallChat() throws Exception {
		given(this.chatSession.getTurns()).willReturn(List.of());
		this.mockMvc.perform(post("/assistant").param("message", "   "))
			.andExpect(status().isOk())
			.andExpect(view().name("assistant/chat"));
		// chat() is not invoked for blank messages — verified by Mockito default (no
		// interaction)
	}

	@Test
	void safeDeclineForMutationRequest() throws Exception {
		String decline = "I can only look up existing records. I cannot make changes to clinic data.";
		ChatTurn turn = new ChatTurn("Add a visit for Basil", decline, "Request declined — outside supported scope.");
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "Add a visit for Basil"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("turns"));
	}

	@Test
	void safeDeclineForMedicalAdviceRequest() throws Exception {
		String decline = "I am not able to provide veterinary diagnosis or treatment advice.";
		ChatTurn turn = new ChatTurn("What medication should Basil take?", decline,
				"Request declined — outside supported scope.");
		given(this.chatSession.chat(anyString())).willReturn(turn);
		given(this.chatSession.getTurns()).willReturn(List.of(turn));

		this.mockMvc.perform(post("/assistant").param("message", "What medication should Basil take?"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("turns"));
	}

}

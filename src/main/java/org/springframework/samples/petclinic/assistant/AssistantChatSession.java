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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * Session-scoped service that maintains temporary in-memory conversation history for one
 * staff session. No transcript is persisted; history is lost when the session ends.
 */
@Component
@SessionScope
class AssistantChatSession {

	private static final Logger logger = LoggerFactory.getLogger(AssistantChatSession.class);

	private final ChatClient chatClient;

	private final ClinicQueryService queryService;

	private final List<ChatTurn> turns = new ArrayList<>();

	private final List<Message> history = new ArrayList<>();

	/**
	 * Records the tools actually invoked during the current chat turn, in order.
	 * Populated by the {@code @Tool} methods while the model call is in flight and
	 * consumed by {@link #buildTrace}. Reset at the start of every turn so a trace never
	 * reports a lookup that did not run.
	 */
	private final List<ToolInvocation> currentTurnInvocations = new ArrayList<>();

	AssistantChatSession(ChatClient.Builder chatClientBuilder, ClinicQueryService queryService) {
		this.chatClient = chatClientBuilder.build();
		this.queryService = queryService;
	}

	ChatTurn chat(String userMessage) {
		logger.debug("Staff chat turn: [{}]", userMessage);

		// Build messages list: history + current user message
		List<Message> messages = new ArrayList<>(this.history);
		messages.add(new UserMessage(userMessage));

		this.currentTurnInvocations.clear();
		boolean callFailed = false;
		String reply;
		try {
			reply = this.chatClient.prompt().system(SystemPrompt.TEXT).messages(messages).tools(this).call().content();
		}
		catch (RuntimeException ex) {
			logger.warn("Chat call failed", ex);
			callFailed = true;
			reply = "I was unable to reach the assistant service. Please try again.";
		}

		if (reply == null) {
			reply = "No response received.";
		}

		// Update in-memory history
		this.history.add(new UserMessage(userMessage));
		this.history.add(new AssistantMessage(reply));

		String trace = buildTrace(callFailed);
		ChatTurn turn = new ChatTurn(userMessage, reply, trace);
		this.turns.add(turn);
		return turn;
	}

	List<ChatTurn> getTurns() {
		return Collections.unmodifiableList(this.turns);
	}

	@Tool(description = """
			Find clinic owners by partial, case-insensitive last name. \
			Returns up to five candidates; each record includes full name, city, telephone, and pets. \
			When multiple matches are returned, list all candidates and ask staff to narrow the request \
			— do not select an identity. When the result is empty, explicitly admit the record is absent.""")
	List<OwnerRecord> findOwnersByLastName(String lastName) {
		logger.debug("Tool call: findOwnersByLastName({})", lastName);
		List<OwnerRecord> results = this.queryService.findOwnersByLastName(lastName);
		this.currentTurnInvocations.add(new ToolInvocation("owner", results.size()));
		return results;
	}

	@Tool(description = """
			Find pets by partial, case-insensitive name. \
			Returns up to five candidates; each record includes pet name, species, owner name, and visit history. \
			When multiple matches are returned, list all candidates and ask staff to narrow the request. \
			When the result is empty, explicitly admit the record is absent.""")
	List<PetRecord> findPetsByName(String petName) {
		logger.debug("Tool call: findPetsByName({})", petName);
		List<PetRecord> results = this.queryService.findPetsByName(petName);
		this.currentTurnInvocations.add(new ToolInvocation("pet", results.size()));
		return results;
	}

	@Tool(description = "List all veterinarians and their specialties.")
	List<VetRecord> listVets() {
		logger.debug("Tool call: listVets()");
		List<VetRecord> results = this.queryService.listVets();
		this.currentTurnInvocations.add(new ToolInvocation("vet", results.size()));
		return results;
	}

	/**
	 * Build a concise, one-line activity trace grounded in what actually happened this
	 * turn: whether the model call failed, which read-only tools ran, and how many
	 * records each returned. When no tool ran, the trace says so plainly rather than
	 * inferring a lookup from message wording — the assistant must not claim a data
	 * lookup it did not perform.
	 * @param callFailed whether the underlying chat call threw before completing
	 */
	private String buildTrace(boolean callFailed) {
		if (callFailed) {
			return "Assistant service call failed — no clinic data was retrieved.";
		}
		if (this.currentTurnInvocations.isEmpty()) {
			return "Answered without querying clinic data; no lookup tool was invoked.";
		}
		List<String> parts = new ArrayList<>();
		for (ToolInvocation invocation : this.currentTurnInvocations) {
			parts.add(invocation.describe());
		}
		return String.join(" ", parts);
	}

	/**
	 * A single read-only tool execution within a turn: which record kind was queried and
	 * how many records it returned. Used to ground {@link #buildTrace} in real outcomes.
	 */
	private record ToolInvocation(String kind, int resultCount) {

		String describe() {
			if (this.resultCount == 0) {
				return "Looked up " + this.kind + " records — none found in PetClinic data.";
			}
			if (this.resultCount == 1) {
				return "Looked up " + this.kind + " records — 1 match in PetClinic data.";
			}
			return "Looked up " + this.kind + " records — " + this.resultCount + " matches in PetClinic data.";
		}

	}

}

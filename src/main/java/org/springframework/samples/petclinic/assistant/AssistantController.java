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

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Staff-facing Clinic Assistant page. Renders the chat form, submits turns, and displays
 * session-scoped conversation history.
 */
@Controller
@RequestMapping("/assistant")
class AssistantController {

	private final AssistantChatSession chatSession;

	AssistantController(AssistantChatSession chatSession) {
		this.chatSession = chatSession;
	}

	@GetMapping
	public String showAssistant(Model model) {
		model.addAttribute("turns", this.chatSession.getTurns());
		return "assistant/chat";
	}

	@PostMapping
	public String submitTurn(@RequestParam("message") String message, Model model) {
		if (message != null && !message.isBlank()) {
			this.chatSession.chat(message.strip());
		}
		model.addAttribute("turns", this.chatSession.getTurns());
		return "assistant/chat";
	}

}

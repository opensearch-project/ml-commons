/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.searchpipelines.questionanswering.generative.prompt;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.text.StringEscapeUtils;
import org.opensearch.ml.common.conversation.Interaction;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for producing prompts for LLMs.
 *
 * TODO Should prompt engineering llm-specific?
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PromptUtil {

    public static final String DEFAULT_CHAT_COMPLETION_PROMPT_TEMPLATE =
        "Generate a concise and informative answer in less than 100 words for the given question, taking into context: "
            + "- An enumerated list of search results"
            + "- A rephrase of the question that was used to generate the search results"
            + "- The conversation history"
            + "Cite search results using [${number}] notation."
            + "Do not repeat yourself, and NEVER repeat anything in the chat history."
            + "If there are any necessary steps or procedures in your answer, enumerate them.";

    private static final String roleUser = "user";

    public static String getQuestionRephrasingPrompt(String originalQuestion, List<Interaction> chatHistory) {
        return null;
    }

    public static String getChatCompletionPrompt(String question, List<Interaction> chatHistory, List<String> contexts) {
        return buildMessageParameter(question, chatHistory, contexts);
    }

    enum Role {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system");

        // TODO Add "function"

        @Getter
        private String name;

        Role(String name) {
            this.name = name;
        }
    }

    @VisibleForTesting
    static String buildMessageParameter(String question, List<Interaction> chatHistory, List<String> contexts) {

        // TODO better prompt template management is needed here.

        JsonArray messageArray = new JsonArray();
        messageArray.add(new Message(Role.USER, DEFAULT_CHAT_COMPLETION_PROMPT_TEMPLATE).toJson());
        for (String result : contexts) {
            messageArray.add(new Message(Role.USER, "SEARCH RESULT: " + result).toJson());
        }
        if (!chatHistory.isEmpty()) {
            Messages.fromInteractions(chatHistory).getMessages().forEach(m -> messageArray.add(m.toJson()));
        }
        messageArray.add(new Message(Role.USER, "QUESTION: " + question).toJson());
        messageArray.add(new Message(Role.USER, "ANSWER:").toJson());

        return messageArray.toString();
    }

    private static Gson gson = new Gson();

    @Getter
    static class Messages {

        @Getter
        private List<Message> messages = new ArrayList<>();
        //private JsonArray jsonArray = new JsonArray();

        public Messages(final List<Message> messages) {
            addMessages(messages);
        }

        public void addMessages(List<Message> messages) {
            this.messages.addAll(messages);
        }

        public static Messages fromInteractions(final List<Interaction> interactions) {
            List<Message> messages = new ArrayList<>();

            for (Interaction interaction : interactions) {
                messages.add(new Message(Role.USER, interaction.getInput()));
                messages.add(new Message(Role.ASSISTANT, interaction.getResponse()));
            }

            return new Messages(messages);
        }
    }

    static class Message {

        private final static String MESSAGE_FIELD_ROLE = "role";
        private final static String MESSAGE_FIELD_CONTENT = "content";

        @Getter
        private Role role;
        @Getter
        private String content;

        private JsonObject json;

        public Message() {
            json = new JsonObject();
        }

        public Message(Role role, String content) {
            this();
            setRole(role);
            setContent(content);
        }

        public void setRole(Role role) {
            json.remove(MESSAGE_FIELD_ROLE);
            json.add(MESSAGE_FIELD_ROLE, new JsonPrimitive(role.getName()));
        }
        public void setContent(String content) {
            this.content = StringEscapeUtils.escapeJson(content);
            json.remove(MESSAGE_FIELD_CONTENT);
            json.add(MESSAGE_FIELD_CONTENT, new JsonPrimitive(this.content));
        }

        public JsonObject toJson() {
            return json;
        }
    }
}

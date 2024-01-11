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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.text.StringEscapeUtils;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.conversation.Interaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A utility class for producing prompts for LLMs.
 *
 * TODO Should prompt engineering llm-specific?
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PromptUtil {

    public static final String DEFAULT_SYSTEM_PROMPT =
        "Generate a concise and informative answer in less than 100 words for the given question, taking into context: "
            + "- An enumerated list of search results"
            + "- A rephrase of the question that was used to generate the search results"
            + "- The conversation history"
            + "Cite search results using [${number}] notation."
            + "Do not repeat yourself, and NEVER repeat anything in the chat history."
            + "If there are any necessary steps or procedures in your answer, enumerate them.";

    private static final String roleUser = "user";

    private static final String NEWLINE = "\\n";

    public static String getQuestionRephrasingPrompt(String originalQuestion, List<Interaction> chatHistory) {
        return null;
    }

    public static String getChatCompletionPrompt(String question, List<Interaction> chatHistory, List<String> contexts) {
        return getChatCompletionPrompt(DEFAULT_SYSTEM_PROMPT, null, question, chatHistory, contexts);
    }

    // TODO Currently, this is OpenAI specific. Change this to indicate as such or address it as part of
    // future prompt template management work.
    public static String getChatCompletionPrompt(
        String systemPrompt,
        String userInstructions,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts
    ) {
        return buildMessageParameter(systemPrompt, userInstructions, question, chatHistory, contexts);
    }

    enum ChatRole {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system");

        // TODO Add "function"

        @Getter
        private String name;

        ChatRole(String name) {
            this.name = name;
        }
    }

    public static String buildSingleStringPrompt(
        String systemPrompt,
        String userInstructions,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts
    ) {
        if (Strings.isNullOrEmpty(systemPrompt) && Strings.isNullOrEmpty(userInstructions)) {
            systemPrompt = DEFAULT_SYSTEM_PROMPT;
        }

        StringBuilder bldr = new StringBuilder();

        if (!Strings.isNullOrEmpty(systemPrompt)) {
            bldr.append(systemPrompt);
            bldr.append(NEWLINE);
        }
        if (!Strings.isNullOrEmpty(userInstructions)) {
            bldr.append(userInstructions);
            bldr.append(NEWLINE);
        }

        for (int i = 0; i < contexts.size(); i++) {
            bldr.append("SEARCH RESULT " + (i + 1) + ": " + contexts.get(i));
            bldr.append(NEWLINE);
        }
        if (!chatHistory.isEmpty()) {
            // The oldest interaction first
            List<Message> messages = Messages.fromInteractions(chatHistory).getMessages();
            Collections.reverse(messages);
            messages.forEach(m -> {
                bldr.append(m.toString());
                bldr.append(NEWLINE);
            });

        }
        bldr.append("QUESTION: " + question);
        bldr.append(NEWLINE);

        return bldr.toString();
    }

    // VisibleForTesting
    static String buildMessageParameter(
        String systemPrompt,
        String userInstructions,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts
    ) {

        // TODO better prompt template management is needed here.

        if (Strings.isNullOrEmpty(systemPrompt) && Strings.isNullOrEmpty(userInstructions)) {
            systemPrompt = DEFAULT_SYSTEM_PROMPT;
        }

        JsonArray messageArray = new JsonArray();

        messageArray.addAll(getPromptTemplateAsJsonArray(systemPrompt, userInstructions));
        for (int i = 0; i < contexts.size(); i++) {
            messageArray.add(new Message(ChatRole.USER, "SEARCH RESULT " + (i + 1) + ": " + contexts.get(i)).toJson());
        }
        if (!chatHistory.isEmpty()) {
            // The oldest interaction first
            List<Message> messages = Messages.fromInteractions(chatHistory).getMessages();
            Collections.reverse(messages);
            messages.forEach(m -> messageArray.add(m.toJson()));
        }
        messageArray.add(new Message(ChatRole.USER, "QUESTION: " + question).toJson());
        messageArray.add(new Message(ChatRole.USER, "ANSWER:").toJson());

        return messageArray.toString();
    }

    public static String getPromptTemplate(String systemPrompt, String userInstructions) {
        return getPromptTemplateAsJsonArray(systemPrompt, userInstructions).toString();
    }

    static JsonArray getPromptTemplateAsJsonArray(String systemPrompt, String userInstructions) {
        JsonArray messageArray = new JsonArray();

        if (!Strings.isNullOrEmpty(systemPrompt)) {
            messageArray.add(new Message(ChatRole.SYSTEM, systemPrompt).toJson());
        }
        if (!Strings.isNullOrEmpty(userInstructions)) {
            messageArray.add(new Message(ChatRole.USER, userInstructions).toJson());
        }
        return messageArray;
    }

    @Getter
    static class Messages {

        @Getter
        private List<Message> messages = new ArrayList<>();

        public Messages(final List<Message> messages) {
            addMessages(messages);
        }

        public void addMessages(List<Message> messages) {
            this.messages.addAll(messages);
        }

        public static Messages fromInteractions(final List<Interaction> interactions) {
            List<Message> messages = new ArrayList<>();

            for (Interaction interaction : interactions) {
                messages.add(new Message(ChatRole.USER, interaction.getInput()));
                messages.add(new Message(ChatRole.ASSISTANT, interaction.getResponse()));
            }

            return new Messages(messages);
        }
    }

    // TODO This is OpenAI specific. Either change this to OpenAiMessage or have it handle
    // vendor specific messages.
    static class Message {

        private final static String MESSAGE_FIELD_ROLE = "role";
        private final static String MESSAGE_FIELD_CONTENT = "content";

        @Getter
        private ChatRole chatRole;
        @Getter
        private String content;

        private JsonObject json;

        public Message() {
            json = new JsonObject();
        }

        public Message(ChatRole chatRole, String content) {
            this();
            setChatRole(chatRole);
            setContent(content);
        }

        public void setChatRole(ChatRole chatRole) {
            this.chatRole = chatRole;
            json.remove(MESSAGE_FIELD_ROLE);
            json.add(MESSAGE_FIELD_ROLE, new JsonPrimitive(chatRole.getName()));
        }

        public void setContent(String content) {
            this.content = StringEscapeUtils.escapeJson(content);
            json.remove(MESSAGE_FIELD_CONTENT);
            json.add(MESSAGE_FIELD_CONTENT, new JsonPrimitive(this.content));
        }

        public JsonObject toJson() {
            return json;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s: %s", chatRole.getName(), content);
        }
    }
}

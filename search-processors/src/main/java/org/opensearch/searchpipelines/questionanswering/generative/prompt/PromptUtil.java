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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.apache.commons.text.StringEscapeUtils;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.searchpipelines.questionanswering.generative.llm.Llm;
import org.opensearch.searchpipelines.questionanswering.generative.llm.MessageBlock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

    public static String getChatCompletionPrompt(
        Llm.ModelProvider provider,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts
    ) {
        return getChatCompletionPrompt(provider, DEFAULT_SYSTEM_PROMPT, null, question, chatHistory, contexts, null);
    }

    // TODO Currently, this is OpenAI specific. Change this to indicate as such or address it as part of
    // future prompt template management work.
    public static String getChatCompletionPrompt(
        Llm.ModelProvider provider,
        String systemPrompt,
        String userInstructions,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts,
        List<MessageBlock> llmMessages
    ) {
        return buildMessageParameter(provider, systemPrompt, userInstructions, question, chatHistory, contexts, llmMessages);
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

    /**
     * Message APIs such as OpenAI's Chat Completion API and Anthropic's Messages API
     * use an array of messages as input to the LLM and they are better suited for
     * multi-modal interactions using text and images.
     *
     * @param provider
     * @param systemPrompt
     * @param userInstructions
     * @param question
     * @param chatHistory
     * @param contexts
     * @return
     */
    @VisibleForTesting
    static String buildMessageParameter(
        Llm.ModelProvider provider,
        String systemPrompt,
        String userInstructions,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts
    ) {
        return buildMessageParameter(provider, systemPrompt, userInstructions, question, chatHistory, contexts, null);
    }

    static String buildMessageParameter(
        Llm.ModelProvider provider,
        String systemPrompt,
        String userInstructions,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts,
        List<MessageBlock> llmMessages
    ) {

        // TODO better prompt template management is needed here.

        if (Strings.isNullOrEmpty(systemPrompt) && Strings.isNullOrEmpty(userInstructions)) {
            // Some model providers such as Anthropic do not allow the system prompt as part of the message body.
            userInstructions = DEFAULT_SYSTEM_PROMPT;
        }

        MessageArrayBuilder bldr = new MessageArrayBuilder(provider);

        // Build the system prompt (only one per conversation/session)
        if (!Strings.isNullOrEmpty(systemPrompt)) {
            bldr.startMessage(ChatRole.SYSTEM);
            bldr.addTextContent(systemPrompt);
            bldr.endMessage();
        }

        // Anthropic does not allow two consecutive messages of the same role
        // so we combine all user messages and an array of contents.
        bldr.startMessage(ChatRole.USER);
        boolean lastRoleIsAssistant = false;
        if (!Strings.isNullOrEmpty(userInstructions)) {
            bldr.addTextContent(userInstructions);
        }

        for (int i = 0; i < contexts.size(); i++) {
            bldr.addTextContent("SEARCH RESULT " + (i + 1) + ": " + contexts.get(i));
        }

        if (!chatHistory.isEmpty()) {
            // The oldest interaction first
            int idx = chatHistory.size() - 1;
            Interaction firstInteraction = chatHistory.get(idx);
            bldr.addTextContent(firstInteraction.getInput());
            bldr.endMessage();
            bldr.startMessage(ChatRole.ASSISTANT, firstInteraction.getResponse());
            bldr.endMessage();

            if (chatHistory.size() > 1) {
                for (int i = --idx; i >= 0; i--) {
                    Interaction interaction = chatHistory.get(i);
                    bldr.startMessage(ChatRole.USER, interaction.getInput());
                    bldr.endMessage();
                    bldr.startMessage(ChatRole.ASSISTANT, interaction.getResponse());
                    bldr.endMessage();
                }
            }

            lastRoleIsAssistant = true;
        }

        if (llmMessages != null && !llmMessages.isEmpty()) {
            // TODO MessageBlock can have assistant roles for few-shot prompting.
            if (lastRoleIsAssistant) {
                bldr.startMessage(ChatRole.USER);
            }
            for (MessageBlock message : llmMessages) {
                List<MessageBlock.AbstractBlock> blockList = message.getBlockList();
                for (MessageBlock.Block block : blockList) {
                    switch (block.getType()) {
                        case "text":
                            bldr.addTextContent(((MessageBlock.TextBlock) block).getText());
                            break;
                        case "image":
                            MessageBlock.ImageBlock ib = (MessageBlock.ImageBlock) block;
                            if (ib.getData() != null) {
                                bldr.addImageData(ib.getFormat(), ib.getData());
                            } else if (ib.getUrl() != null) {
                                bldr.addImageUrl(ib.getFormat(), ib.getUrl());
                            }
                            break;
                        case "document":
                            MessageBlock.DocumentBlock db = (MessageBlock.DocumentBlock) block;
                            bldr.addDocumentContent(db.getFormat(), db.getName(), db.getData());
                            break;
                        default:
                            break;
                    }
                }
            }
        } else {
            if (lastRoleIsAssistant) {
                bldr.startMessage(ChatRole.USER, "QUESTION: " + question + "\n");
            } else {
                bldr.addTextContent("QUESTION: " + question + "\n");
            }
            bldr.addTextContent("ANSWER:");
        }

        bldr.endMessage();

        return bldr.toJsonArray().toString();
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

    /*
    static JsonArray getPromptTemplateAsJsonArray(Llm.ModelProvider provider, String systemPrompt, String userInstructions) {
    
        MessageArrayBuilder bldr = new MessageArrayBuilder(provider);
    
        if (!Strings.isNullOrEmpty(systemPrompt)) {
            bldr.startMessage(ChatRole.SYSTEM);
            bldr.addTextContent(systemPrompt);
            bldr.endMessage();
        }
        if (!Strings.isNullOrEmpty(userInstructions)) {
            bldr.startMessage(ChatRole.USER);
            bldr.addTextContent(userInstructions);
            bldr.endMessage();
        }
        return bldr.toJsonArray();
    }*/

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

    interface Content {

        // All content blocks accept text
        void addText(String text);

        JsonElement toJson();
    }

    interface ImageContent extends Content {

        void addImageData(String format, String data);

        void addImageUrl(String format, String url);
    }

    interface DocumentContent extends Content {
        void addDocument(String format, String name, String data);
    }

    interface MultimodalContent extends ImageContent, DocumentContent {

    }

    private final static String CONTENT_FIELD_TEXT = "text";
    private final static String CONTENT_FIELD_TYPE = "type";

    static class OpenAIContent implements ImageContent {

        private JsonArray json;

        public OpenAIContent() {
            this.json = new JsonArray();
        }

        @Override
        public void addText(String text) {
            JsonObject content = new JsonObject();
            content.add(CONTENT_FIELD_TYPE, new JsonPrimitive(CONTENT_FIELD_TEXT));
            content.add(CONTENT_FIELD_TEXT, new JsonPrimitive(text));
            json.add(content);
        }

        @Override
        public void addImageData(String format, String data) {
            JsonObject content = new JsonObject();
            content.add("type", new JsonPrimitive("image_url"));
            JsonObject urlContent = new JsonObject();
            String imageData = String.format(Locale.ROOT, "data:image/%s;base64,%s", format, data);
            urlContent.add("url", new JsonPrimitive(imageData));
            content.add("image_url", urlContent);
            json.add(content);
        }

        @Override
        public void addImageUrl(String format, String url) {
            JsonObject content = new JsonObject();
            content.add("type", new JsonPrimitive("image_url"));
            JsonObject urlContent = new JsonObject();
            urlContent.add("url", new JsonPrimitive(url));
            content.add("image_url", urlContent);
            json.add(content);
        }

        @Override
        public JsonElement toJson() {
            return this.json;
        }
    }

    static class BedrockContent implements MultimodalContent {

        private JsonArray json;

        public BedrockContent() {
            this.json = new JsonArray();
        }

        public BedrockContent(String type, String value) {
            this.json = new JsonArray();
            if (type.equals("text")) {
                addText(value);
            }
        }

        @Override
        public void addText(String text) {
            JsonObject content = new JsonObject();
            content.add(CONTENT_FIELD_TEXT, new JsonPrimitive(text));
            json.add(content);
        }

        @Override
        public JsonElement toJson() {
            return this.json;
        }

        @Override
        public void addImageData(String format, String data) {
            JsonObject imageData = new JsonObject();
            imageData.add("bytes", new JsonPrimitive(data));
            JsonObject image = new JsonObject();
            image.add("format", new JsonPrimitive(format));
            image.add("source", imageData);
            JsonObject content = new JsonObject();
            content.add("image", image);
            json.add(content);
        }

        @Override
        public void addImageUrl(String format, String url) {
            // Bedrock does not support image URLs.
        }

        @Override
        public void addDocument(String format, String name, String data) {
            JsonObject documentData = new JsonObject();
            documentData.add("bytes", new JsonPrimitive(data));
            JsonObject document = new JsonObject();
            document.add("format", new JsonPrimitive(format));
            document.add("name", new JsonPrimitive(name));
            document.add("source", documentData);
            JsonObject content = new JsonObject();
            content.add("document", document);
            json.add(content);
        }
    }

    static class MessageArrayBuilder {

        private final Llm.ModelProvider provider;
        private List<Message> messages = new ArrayList<>();
        private Message message = null;
        private Content content = null;

        public MessageArrayBuilder(Llm.ModelProvider provider) {
            // OpenAI or Bedrock Converse API
            if (!EnumSet.of(Llm.ModelProvider.OPENAI, Llm.ModelProvider.BEDROCK_CONVERSE).contains(provider)) {
                throw new IllegalArgumentException("Unsupported provider: " + provider);
            }
            this.provider = provider;
        }

        public void startMessage(ChatRole role) {
            this.message = new Message();
            this.message.setChatRole(role);
            if (this.provider == Llm.ModelProvider.OPENAI) {
                content = new OpenAIContent();
            } else if (this.provider == Llm.ModelProvider.BEDROCK_CONVERSE) {
                content = new BedrockContent();
            }
        }

        public void startMessage(ChatRole role, String text) {
            startMessage(role);
            addTextContent(text);
        }

        public void endMessage() {
            this.message.setContent(this.content);
            this.messages.add(this.message);
            message = null;
            content = null;
        }

        public void addTextContent(String content) {
            if (this.message == null || this.content == null) {
                throw new RuntimeException("You must call startMessage before calling addTextContent !!");
            }
            this.content.addText(content);
        }

        public void addImageData(String format, String data) {
            if (this.content != null && this.content instanceof ImageContent) {
                ((ImageContent) this.content).addImageData(format, data);
            }
        }

        public void addImageUrl(String format, String url) {
            if (this.content != null && this.content instanceof ImageContent) {
                ((ImageContent) this.content).addImageUrl(format, url);
            }
        }

        public void addDocumentContent(String format, String name, String data) {
            if (this.content != null && this.content instanceof DocumentContent) {
                ((DocumentContent) this.content).addDocument(format, name, data);
            }
        }

        public JsonArray toJsonArray() {
            Preconditions
                .checkState(this.message == null && this.content == null, "You must call endMessage before calling toJsonArray !!");

            JsonArray ja = new JsonArray();
            for (Message message : messages) {
                ja.add(message.toJson());
            }
            return ja;
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

        public Message(ChatRole chatRole, Content content) {
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

        public void setContent(Content content) {
            json.remove(MESSAGE_FIELD_CONTENT);
            json.add(MESSAGE_FIELD_CONTENT, content.toJson());
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

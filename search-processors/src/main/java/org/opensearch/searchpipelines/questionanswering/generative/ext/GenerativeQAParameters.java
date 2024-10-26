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
package org.opensearch.searchpipelines.questionanswering.generative.ext;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.opensearch.Version;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.searchpipelines.questionanswering.generative.llm.MessageBlock;

import com.google.common.base.Preconditions;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines parameters for generative QA search pipelines.
 *
 */

@NoArgsConstructor
public class GenerativeQAParameters implements Writeable, ToXContentObject {

    // Optional parameter; if provided, conversational memory will be used for RAG
    // and the current interaction will be saved in the conversation referenced by this id.
    private static final String CONVERSATION_ID = "memory_id";

    // Optional parameter; if an LLM model is not set at the search pipeline level, one must be
    // provided at the search request level.
    private static final String LLM_MODEL = "llm_model";

    // Required parameter; this is sent to LLMs as part of the user prompt.
    // TODO support question rewriting when chat history is not used (conversation_id is not provided).
    private static final String LLM_QUESTION = "llm_question";

    // Optional parameter; this parameter controls the number of search results ("contexts") to
    // include in the user prompt.
    private static final String CONTEXT_SIZE = "context_size";

    // Optional parameter; this parameter controls the number of the interactions to include
    // in the user prompt.
    private static final String INTERACTION_SIZE = "message_size";

    // Optional parameter; this parameter controls how long the search pipeline waits for a response
    // from a remote inference endpoint before timing out the request.
    private static final String TIMEOUT = "timeout";

    // Optional parameter: this parameter allows request-level customization of the "system" (role) prompt.
    private static final String SYSTEM_PROMPT = "system_prompt";

    // Optional parameter: this parameter allows request-level customization of the "user" (role) prompt.
    private static final String USER_INSTRUCTIONS = "user_instructions";

    // Optional parameter; this parameter indicates the name of the field in the LLM response
    // that contains the chat completion text, i.e. "answer".
    private static final String LLM_RESPONSE_FIELD = "llm_response_field";

    private static final String LLM_MESSAGES_FIELD = "llm_messages";

    public static final int SIZE_NULL_VALUE = -1;

    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_BEDROCK_CONVERSE_LLM_MESSAGES = CommonValue.VERSION_2_18_0;

    @Setter
    @Getter
    private String conversationId;

    @Setter
    @Getter
    private String llmModel;

    @Setter
    @Getter
    private String llmQuestion;

    @Setter
    @Getter
    private Integer contextSize;

    @Setter
    @Getter
    private Integer interactionSize;

    @Setter
    @Getter
    private Integer timeout;

    @Setter
    @Getter
    private String systemPrompt;

    @Setter
    @Getter
    private String userInstructions;

    @Setter
    @Getter
    private String llmResponseField;

    @Setter
    @Getter
    private List<MessageBlock> llmMessages = new ArrayList<>();

    public GenerativeQAParameters(
        String conversationId,
        String llmModel,
        String llmQuestion,
        String systemPrompt,
        String userInstructions,
        Integer contextSize,
        Integer interactionSize,
        Integer timeout,
        String llmResponseField
    ) {
        this(
            conversationId,
            llmModel,
            llmQuestion,
            systemPrompt,
            userInstructions,
            contextSize,
            interactionSize,
            timeout,
            llmResponseField,
            null
        );
    }

    @Builder(toBuilder = true)
    public GenerativeQAParameters(
        String conversationId,
        String llmModel,
        String llmQuestion,
        String systemPrompt,
        String userInstructions,
        Integer contextSize,
        Integer interactionSize,
        Integer timeout,
        String llmResponseField,
        List<MessageBlock> llmMessages
    ) {
        this.conversationId = conversationId;
        this.llmModel = llmModel;

        Preconditions
            .checkArgument(
                !(Strings.isNullOrEmpty(llmQuestion) && (llmMessages == null || llmMessages.isEmpty())),
                "At least one of " + LLM_QUESTION + " or " + LLM_MESSAGES_FIELD + " must be provided."
            );
        this.llmQuestion = llmQuestion;
        this.systemPrompt = systemPrompt;
        this.userInstructions = userInstructions;
        this.contextSize = (contextSize == null) ? SIZE_NULL_VALUE : contextSize;
        this.interactionSize = (interactionSize == null) ? SIZE_NULL_VALUE : interactionSize;
        this.timeout = (timeout == null) ? SIZE_NULL_VALUE : timeout;
        this.llmResponseField = llmResponseField;
        if (llmMessages != null) {
            this.llmMessages.addAll(llmMessages);
        }
    }

    public GenerativeQAParameters(StreamInput input) throws IOException {
        Version version = input.getVersion();
        this.conversationId = input.readOptionalString();
        this.llmModel = input.readOptionalString();

        // this string was made optional in 2.18
        if (version.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_BEDROCK_CONVERSE_LLM_MESSAGES)) {
            this.llmQuestion = input.readOptionalString();
        } else {
            this.llmQuestion = input.readString();
        }

        this.systemPrompt = input.readOptionalString();
        this.userInstructions = input.readOptionalString();
        this.contextSize = input.readInt();
        this.interactionSize = input.readInt();
        this.timeout = input.readInt();
        this.llmResponseField = input.readOptionalString();

        if (version.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_BEDROCK_CONVERSE_LLM_MESSAGES)) {
            this.llmMessages.addAll(input.readList(MessageBlock::new));
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        if (this.conversationId != null) {
            xContentBuilder.field(CONVERSATION_ID, this.conversationId);
        }

        if (this.llmModel != null) {
            xContentBuilder.field(LLM_MODEL, this.llmModel);
        }

        if (this.llmQuestion != null) {
            xContentBuilder.field(LLM_QUESTION, this.llmQuestion);
        }

        if (this.systemPrompt != null) {
            xContentBuilder.field(SYSTEM_PROMPT, this.systemPrompt);
        }

        if (this.userInstructions != null) {
            xContentBuilder.field(USER_INSTRUCTIONS, this.userInstructions);
        }

        if (this.contextSize != null) {
            xContentBuilder.field(CONTEXT_SIZE, this.contextSize);
        }

        if (this.interactionSize != null) {
            xContentBuilder.field(INTERACTION_SIZE, this.interactionSize);
        }

        if (this.timeout != null) {
            xContentBuilder.field(TIMEOUT, this.timeout);
        }

        if (this.llmResponseField != null) {
            xContentBuilder.field(LLM_RESPONSE_FIELD, this.llmResponseField);
        }

        if (this.llmMessages != null && !this.llmMessages.isEmpty()) {
            xContentBuilder.field(LLM_MESSAGES_FIELD, this.llmMessages);
        }

        xContentBuilder.endObject();
        return xContentBuilder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version version = out.getVersion();
        out.writeOptionalString(conversationId);
        out.writeOptionalString(llmModel);

        // this string was made optional in 2.18
        if (version.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_BEDROCK_CONVERSE_LLM_MESSAGES)) {
            out.writeOptionalString(llmQuestion);
        } else {
            out.writeString(llmQuestion);
        }

        out.writeOptionalString(systemPrompt);
        out.writeOptionalString(userInstructions);
        out.writeInt(contextSize);
        out.writeInt(interactionSize);
        out.writeInt(timeout);
        out.writeOptionalString(llmResponseField);

        if (version.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_BEDROCK_CONVERSE_LLM_MESSAGES)) {
            out.writeList(llmMessages);
        }
    }

    public static GenerativeQAParameters parse(XContentParser parser) throws IOException {
        String conversationId = null;
        String llmModel = null;
        String llmQuestion = null;
        String systemPrompt = null;
        String userInstructions = null;
        Integer contextSize = null;
        Integer interactionSize = null;
        Integer timeout = null;
        String llmResponseField = null;
        List<MessageBlock> llmMessages = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();

            switch (field) {
                case CONVERSATION_ID:
                    conversationId = parser.text();
                    break;
                case LLM_MODEL:
                    llmModel = parser.text();
                    break;
                case LLM_QUESTION:
                    llmQuestion = parser.text();
                    break;
                case SYSTEM_PROMPT:
                    systemPrompt = parser.text();
                    break;
                case USER_INSTRUCTIONS:
                    userInstructions = parser.text();
                    break;
                case CONTEXT_SIZE:
                    contextSize = parser.intValue();
                    break;
                case INTERACTION_SIZE:
                    interactionSize = parser.intValue();
                    break;
                case TIMEOUT:
                    timeout = parser.intValue();
                    break;
                case LLM_RESPONSE_FIELD:
                    llmResponseField = parser.text();
                    break;
                case LLM_MESSAGES_FIELD:
                    llmMessages = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        llmMessages.add(MessageBlock.fromXContent(parser));
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return GenerativeQAParameters
            .builder()
            .conversationId(conversationId)
            .llmModel(llmModel)
            .llmQuestion(llmQuestion)
            .systemPrompt(systemPrompt)
            .userInstructions(userInstructions)
            .contextSize(contextSize)
            .interactionSize(interactionSize)
            .timeout(timeout)
            .llmResponseField(llmResponseField)
            .llmMessages(llmMessages)
            .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GenerativeQAParameters other = (GenerativeQAParameters) o;
        return Objects.equals(this.conversationId, other.getConversationId())
            && Objects.equals(this.llmModel, other.getLlmModel())
            && Objects.equals(this.llmQuestion, other.getLlmQuestion())
            && Objects.equals(this.systemPrompt, other.getSystemPrompt())
            && Objects.equals(this.userInstructions, other.getUserInstructions())
            && (this.contextSize == other.getContextSize())
            && (this.interactionSize == other.getInteractionSize())
            && (this.timeout == other.getTimeout())
            && Objects.equals(this.llmResponseField, other.getLlmResponseField());
    }

    public void setMessageBlock(List<MessageBlock> blockList) {
        this.llmMessages = blockList;
    }
}

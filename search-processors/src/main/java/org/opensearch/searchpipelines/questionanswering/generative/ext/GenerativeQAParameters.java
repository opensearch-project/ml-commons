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
import java.util.Objects;

import org.opensearch.core.common.Strings;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

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

    public static final int SIZE_NULL_VALUE = -1;

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

    @Builder
    public GenerativeQAParameters(
        String conversationId,
        String llmModel,
        String llmQuestion,
        Integer contextSize,
        Integer interactionSize,
        Integer timeout
    ) {
        this.conversationId = conversationId;
        this.llmModel = llmModel;

        // TODO: keep this requirement until we can extract the question from the query or from the request processor parameters
        // for question rewriting.
        Preconditions.checkArgument(!Strings.isNullOrEmpty(llmQuestion), LLM_QUESTION + " must be provided.");
        this.llmQuestion = llmQuestion;
        this.contextSize = (contextSize == null) ? SIZE_NULL_VALUE : contextSize;
        this.interactionSize = (interactionSize == null) ? SIZE_NULL_VALUE : interactionSize;
        this.timeout = (timeout == null) ? SIZE_NULL_VALUE : timeout;
    }

    public GenerativeQAParameters(StreamInput input) throws IOException {
        this.conversationId = input.readOptionalString();
        this.llmModel = input.readOptionalString();
        this.llmQuestion = input.readString();
        this.contextSize = input.readInt();
        this.interactionSize = input.readInt();
        this.timeout = input.readInt();
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

        if (this.contextSize != null) {
            xContentBuilder.field(CONTEXT_SIZE, this.contextSize);
        }

        if (this.interactionSize != null) {
            xContentBuilder.field(INTERACTION_SIZE, this.interactionSize);
        }

        if (this.timeout != null) {
            xContentBuilder.field(TIMEOUT, this.timeout);
        }

        xContentBuilder.endObject();
        return xContentBuilder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(conversationId);
        out.writeOptionalString(llmModel);

        Preconditions.checkNotNull(llmQuestion, "llm_question must not be null.");
        out.writeString(llmQuestion);
        out.writeInt(contextSize);
        out.writeInt(interactionSize);
        out.writeInt(timeout);
    }

    public static GenerativeQAParameters parse(XContentParser parser) throws IOException {
        String conversationId = null;
        String llmModel = null;
        String llmQuestion = null;
        Integer contextSize = null;
        Integer interactionSize = null;
        Integer timeout = null;

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
                case CONTEXT_SIZE:
                    contextSize = parser.intValue();
                    break;
                case INTERACTION_SIZE:
                    interactionSize = parser.intValue();
                    break;
                case TIMEOUT:
                    timeout = parser.intValue();
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
            .contextSize(contextSize)
            .interactionSize(interactionSize)
            .timeout(timeout)
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
            && (this.contextSize == other.getContextSize())
            && (this.interactionSize == other.getInteractionSize())
            && (this.timeout == other.getTimeout());
    }
}

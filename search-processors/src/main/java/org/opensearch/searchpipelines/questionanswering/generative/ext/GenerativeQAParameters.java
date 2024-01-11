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

import java.io.IOException;
import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.opensearch.core.ParseField;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Defines parameters for generative QA search pipelines.
 *
 */

@NoArgsConstructor
public class GenerativeQAParameters implements Writeable, ToXContentObject {

    private static final ObjectParser<GenerativeQAParameters, Void> PARSER;

    // Optional parameter; if provided, conversational memory will be used for RAG
    // and the current interaction will be saved in the conversation referenced by this id.
    private static final ParseField CONVERSATION_ID = new ParseField("conversation_id");

    // Optional parameter; if an LLM model is not set at the search pipeline level, one must be
    // provided at the search request level.
    private static final ParseField LLM_MODEL = new ParseField("llm_model");

    // Required parameter; this is sent to LLMs as part of the user prompt.
    // TODO support question rewriting when chat history is not used (conversation_id is not provided).
    private static final ParseField LLM_QUESTION = new ParseField("llm_question");

    // Optional parameter; this parameter controls the number of search results ("contexts") to
    // include in the user prompt.
    private static final ParseField CONTEXT_SIZE = new ParseField("context_size");

    // Optional parameter; this parameter controls the number of the interactions to include
    // in the user prompt.
    private static final ParseField INTERACTION_SIZE = new ParseField("interaction_size");

    // Optional parameter; this parameter controls how long the search pipeline waits for a response
    // from a remote inference endpoint before timing out the request.
    private static final ParseField TIMEOUT = new ParseField("timeout");

    public static final int SIZE_NULL_VALUE = -1;

    static {
        PARSER = new ObjectParser<>("generative_qa_parameters", GenerativeQAParameters::new);
        PARSER.declareString(GenerativeQAParameters::setConversationId, CONVERSATION_ID);
        PARSER.declareString(GenerativeQAParameters::setLlmModel, LLM_MODEL);
        PARSER.declareString(GenerativeQAParameters::setLlmQuestion, LLM_QUESTION);
        PARSER.declareIntOrNull(GenerativeQAParameters::setContextSize, SIZE_NULL_VALUE, CONTEXT_SIZE);
        PARSER.declareIntOrNull(GenerativeQAParameters::setInteractionSize, SIZE_NULL_VALUE, INTERACTION_SIZE);
        PARSER.declareIntOrNull(GenerativeQAParameters::setTimeout, SIZE_NULL_VALUE, TIMEOUT);
    }

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
        Validate.isTrue(!Strings.isNullOrEmpty(llmQuestion), LLM_QUESTION.getPreferredName() + " must be provided.");
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
        return xContentBuilder
            .field(CONVERSATION_ID.getPreferredName(), this.conversationId)
            .field(LLM_MODEL.getPreferredName(), this.llmModel)
            .field(LLM_QUESTION.getPreferredName(), this.llmQuestion)
            .field(CONTEXT_SIZE.getPreferredName(), this.contextSize)
            .field(INTERACTION_SIZE.getPreferredName(), this.interactionSize)
            .field(TIMEOUT.getPreferredName(), this.timeout);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(conversationId);
        out.writeOptionalString(llmModel);

        Objects.requireNonNull(llmQuestion, "llm_question must not be null.");
        out.writeString(llmQuestion);
        out.writeInt(contextSize);
        out.writeInt(interactionSize);
        out.writeInt(timeout);
    }

    public static GenerativeQAParameters parse(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
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

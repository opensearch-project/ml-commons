/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.ParseField;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

/**
 * Defines parameters for generative QA search pipelines.
 *
 */
@AllArgsConstructor
@NoArgsConstructor
public class GenerativeQAParameters implements Writeable, ToXContentObject {

    private static final ObjectParser<GenerativeQAParameters, Void> PARSER;

    private static final ParseField CONVERSATION_ID = new ParseField("conversation_id");
    private static final ParseField LLM_MODEL = new ParseField("llm_model");
    private static final ParseField LLM_QUESTION = new ParseField("llm_question");

    static {
        PARSER = new ObjectParser<>("generative_qa_parameters", GenerativeQAParameters::new);
        PARSER.declareString(GenerativeQAParameters::setConversationId, CONVERSATION_ID);
        PARSER.declareString(GenerativeQAParameters::setLlmModel, LLM_MODEL);
        PARSER.declareString(GenerativeQAParameters::setLlmQuestion, LLM_QUESTION);
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

    public GenerativeQAParameters(StreamInput input) throws IOException {
        this.conversationId = input.readOptionalString();
        this.llmModel = input.readOptionalString();
        this.llmQuestion = input.readString();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return xContentBuilder.field(CONVERSATION_ID.getPreferredName(), this.conversationId)
            .field(LLM_MODEL.getPreferredName(), this.llmModel)
            .field(LLM_QUESTION.getPreferredName(), this.llmQuestion);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (conversationId != null) {
            out.writeString(conversationId);
        }
        if (llmModel != null) {
            out.writeString(llmModel);
        }
        if (llmQuestion != null) {
            out.writeString(llmQuestion);
        }
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
            && Objects.equals(this.llmQuestion, other.getLlmQuestion());
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.sentence_transformer;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteInput;
import org.opensearch.ml.common.input.Input;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@ExecuteInput(algorithms={FunctionName.SENTENCE_TRANSFORMER})
@Data
@AllArgsConstructor
public class SentenceTransformerInput implements Input {

    public static final String SENTENCES_FIELD = "sentences";
    public static final String INDEX_FIELD = "index";
    public static final String DOC_URL_FIELD = "doc_url";
    public static final String TOP_LEVEL_FIELD = "top_level";
    public static final String QUESTION_FIELD = "questions";
    public static final String NOT_RETURN_EMBEDDING_FIELD = "not_return_embedding";
    public static final String SHORT_ANSWER_FIELD = "short_answer";
    public static final String REMOTE_INFERENCE = "remote_inference";
    public static final String GRPC_TARGET = "endpoint";

    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY_ENTRY = new NamedXContentRegistry.Entry(
            Input.class,
            new ParseField(FunctionName.SENTENCE_TRANSFORMER.name()),
            parser -> parse(parser)
    );

    private final List<String> sentences;
    private final String index;
    private final String docUrl;
    private final Boolean topLevel;
    private final Boolean notReturnEmbedding;
    private final Boolean shortAnswer;
    private final List<String> questions;
    private final Boolean remoteInference;
    private final String grpcTarget;

    public SentenceTransformerInput(StreamInput in) throws IOException {
        this.sentences = in.readStringList();
        this.index = in.readOptionalString();
        this.docUrl = in.readOptionalString();
        this.topLevel = in.readOptionalBoolean();
        this.notReturnEmbedding = in.readOptionalBoolean();
        this.shortAnswer = in.readOptionalBoolean();
        this.questions = in.readOptionalStringList();
        this.remoteInference = in.readOptionalBoolean();
        this.grpcTarget = in.readOptionalString();
    }

    public SentenceTransformerInput(List<String> sentences) {
        this.sentences = sentences;
        this.index = null;
        this.docUrl = null;
        this.topLevel = null;
        this.notReturnEmbedding = false;
        this.shortAnswer = null;
        this.questions = null;
        this.remoteInference = false;
        this.grpcTarget = null;
    }

    public static SentenceTransformerInput parse(XContentParser parser) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        List<String> sentences = new ArrayList<>();
        String index = null;
        String docUrl = null;
        Boolean topLevel = null;
        Boolean notReturnEmbedding = false;
        Boolean shortAnswer = false;
        List<String> questions = new ArrayList<>();
        Boolean remoteInference = false;
        String grpcTarget = null;

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case SENTENCES_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        sentences.add(parser.text());
                    }
                    break;
                case INDEX_FIELD:
                    index = parser.text();
                    break;
                case DOC_URL_FIELD:
                    docUrl = parser.text();
                    break;
                case TOP_LEVEL_FIELD:
                    topLevel = parser.booleanValue();
                    break;
                case NOT_RETURN_EMBEDDING_FIELD:
                    notReturnEmbedding = parser.booleanValue();
                    break;
                case SHORT_ANSWER_FIELD:
                    shortAnswer = parser.booleanValue();
                    break;
                case QUESTION_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        questions.add(parser.text());
                    }
                    break;
                case REMOTE_INFERENCE:
                    remoteInference = parser.booleanValue();
                    break;
                case GRPC_TARGET:
                    grpcTarget = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new SentenceTransformerInput(sentences, index, docUrl, topLevel, notReturnEmbedding, shortAnswer, questions, remoteInference, grpcTarget);
    }

    @Override
    public FunctionName getFunctionName() {
        return FunctionName.SENTENCE_TRANSFORMER;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(SENTENCES_FIELD, sentences.toArray());
        if (index !=  null) {
            builder.field(INDEX_FIELD, index);
        }
        if (docUrl != null) {
            builder.field(DOC_URL_FIELD, docUrl);
        }
        if (topLevel != null) {
            builder.field(TOP_LEVEL_FIELD, topLevel);
        }
        if (notReturnEmbedding != null) {
            builder.field(NOT_RETURN_EMBEDDING_FIELD, notReturnEmbedding);
        }
        if (shortAnswer != null) {
            builder.field(SHORT_ANSWER_FIELD, shortAnswer);
        }
        if (questions != null) {
            builder.field(QUESTION_FIELD, questions.toArray());
        }
        if (remoteInference != null) {
            builder.field(REMOTE_INFERENCE, remoteInference);
        }
        if (grpcTarget != null) {
            builder.field(GRPC_TARGET, grpcTarget);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeStringCollection(sentences);
        out.writeOptionalString(index);
        out.writeOptionalString(docUrl);
        out.writeOptionalBoolean(topLevel);
        out.writeOptionalBoolean(notReturnEmbedding);
        out.writeOptionalBoolean(shortAnswer);
        out.writeOptionalStringCollection(questions);
        out.writeOptionalBoolean(remoteInference);
        out.writeOptionalString(grpcTarget);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.input.nlp;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.QuestionAnsweringInputDataSet;
import org.opensearch.ml.common.input.MLInput;

/**
 * MLInput which supports a question answering algorithm
 * Inputs are question and context. Output is the answer
 */
@org.opensearch.ml.common.annotation.MLInput(functionNames = { FunctionName.QUESTION_ANSWERING })
public class QuestionAnsweringMLInput extends MLInput {

    public QuestionAnsweringMLInput(FunctionName algorithm, MLInputDataset dataset) {
        super(algorithm, null, dataset);
    }

    public QuestionAnsweringMLInput(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ALGORITHM_FIELD, algorithm.name());
        if (parameters != null) {
            builder.field(ML_PARAMETERS_FIELD, parameters);
        }
        if (inputDataset != null) {
            QuestionAnsweringInputDataSet ds = (QuestionAnsweringInputDataSet) this.inputDataset;
            String question = ds.getQuestion();
            String context = ds.getContext();
            builder.field(QUESTION_FIELD, question);
            builder.field(CONTEXT_FIELD, context);
        }
        builder.endObject();
        return builder;
    }

    public QuestionAnsweringMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        super();
        this.algorithm = functionName;
        String question = null;
        String context = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case QUESTION_FIELD:
                    question = parser.text();
                    break;
                case CONTEXT_FIELD:
                    context = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        if (question == null) {
            throw new IllegalArgumentException("Question is not provided");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context is not provided");
        }
        inputDataset = new QuestionAnsweringInputDataSet(question, context);
    }

}

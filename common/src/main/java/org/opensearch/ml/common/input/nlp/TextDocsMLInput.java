/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.nlp;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelResultFilter;

/**
 * ML input class which supports a list fo text docs.
 * This class can be used for TEXT_EMBEDDING model.
 */
@org.opensearch.ml.common.annotation.MLInput(functionNames = {
    FunctionName.TEXT_EMBEDDING,
    FunctionName.SPARSE_ENCODING,
    FunctionName.SPARSE_TOKENIZE })
public class TextDocsMLInput extends MLInput {
    public static final String TEXT_DOCS_FIELD = "text_docs";
    public static final String RESULT_FILTER_FIELD = "result_filter";

    public TextDocsMLInput(FunctionName algorithm, MLInputDataset inputDataset) {
        super(algorithm, null, inputDataset);
    }

    public TextDocsMLInput(StreamInput in) throws IOException {
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
            TextDocsInputDataSet textInputDataSet = (TextDocsInputDataSet) this.inputDataset;
            List<String> docs = textInputDataSet.getDocs();
            ModelResultFilter resultFilter = textInputDataSet.getResultFilter();
            if (docs != null && docs.size() > 0) {
                builder.field(TEXT_DOCS_FIELD, docs.toArray(new String[0]));
            }
            if (resultFilter != null) {
                builder.startObject(RESULT_FILTER_FIELD);
                builder.field(RETURN_BYTES_FIELD, resultFilter.isReturnBytes());
                builder.field(RETURN_NUMBER_FIELD, resultFilter.isReturnNumber());
                List<String> targetResponse = resultFilter.getTargetResponse();
                if (targetResponse != null && targetResponse.size() > 0) {
                    builder.field(TARGET_RESPONSE_FIELD, targetResponse.toArray(new String[0]));
                }
                List<Integer> targetPositions = resultFilter.getTargetResponsePositions();
                if (targetPositions != null && targetPositions.size() > 0) {
                    builder.field(TARGET_RESPONSE_POSITIONS_FIELD, targetPositions.toArray(new Integer[0]));
                }
                builder.endObject();
            }
        }
        builder.endObject();
        return builder;
    }

    public TextDocsMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        super();
        this.algorithm = functionName;
        List<String> docs = new ArrayList<>();
        ModelResultFilter resultFilter = null;

        boolean returnBytes = false;
        boolean returnNumber = true;
        List<String> targetResponse = new ArrayList<>();
        List<Integer> targetResponsePositions = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case RETURN_BYTES_FIELD:
                    returnBytes = parser.booleanValue();
                    break;
                case RETURN_NUMBER_FIELD:
                    returnNumber = parser.booleanValue();
                    break;
                case TARGET_RESPONSE_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        targetResponse.add(parser.text());
                    }
                    break;
                case TARGET_RESPONSE_POSITIONS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        targetResponsePositions.add(parser.intValue());
                    }
                    break;
                case TEXT_DOCS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        if (parser.currentToken() == null || parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                            docs.add(null);
                        } else {
                            docs.add(parser.text());
                        }
                    }
                    break;
                case RESULT_FILTER_FIELD:
                    resultFilter = ModelResultFilter.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        ModelResultFilter filter = resultFilter != null
            ? resultFilter
            : ModelResultFilter
                .builder()
                .returnBytes(returnBytes)
                .returnNumber(returnNumber)
                .targetResponse(targetResponse)
                .targetResponsePositions(targetResponsePositions)
                .build();

        if (docs.size() == 0) {
            throw new IllegalArgumentException("Empty text docs");
        }
        inputDataset = new TextDocsInputDataSet(docs, filter);
    }

}

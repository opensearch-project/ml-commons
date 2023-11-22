/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ML input data: algorithm name, parameters and input data set.
 */
@Data
@NoArgsConstructor
public class MLInput implements Input {

    public static final String ALGORITHM_FIELD = "algorithm";
    public static final String ML_PARAMETERS_FIELD = "parameters";
    public static final String INPUT_INDEX_FIELD = "input_index";
    public static final String INPUT_QUERY_FIELD = "input_query";
    public static final String INPUT_DATA_FIELD = "input_data";

    // For trained model
    // Return bytes in model output
    public static final String RETURN_BYTES_FIELD = "return_bytes";
    // Return bytes in model output. This can be used together with return_bytes.
    public static final String RETURN_NUMBER_FIELD = "return_number";
    // Filter target response with name in model output
    public static final String TARGET_RESPONSE_FIELD = "target_response";
    // Filter target response with position in model output
    public static final String TARGET_RESPONSE_POSITIONS_FIELD = "target_response_positions";
    // Input text sentences for text embedding model
    public static final String TEXT_DOCS_FIELD = "text_docs";

    // Algorithm name
    protected FunctionName algorithm;
    // ML algorithm parameters
    protected MLAlgoParams parameters;
    // Input data to train model, run trained model to predict or run ML algorithms(no-model-based) directly.
    protected MLInputDataset inputDataset;

    private int version = 1;

    @Builder(toBuilder = true)
    public MLInput(FunctionName algorithm, MLAlgoParams parameters, MLInputDataset inputDataset) {
        validate(algorithm);
        this.algorithm = algorithm;
        this.parameters = parameters;
        this.inputDataset = inputDataset;
    }

    public MLInput(
        FunctionName algorithm,
        MLAlgoParams parameters,
        SearchSourceBuilder searchSourceBuilder,
        List<String> sourceIndices,
        DataFrame dataFrame,
        MLInputDataset inputDataset
    ) {
        validate(algorithm);
        this.algorithm = algorithm;
        this.parameters = parameters;
        if (inputDataset != null) {
            this.inputDataset = inputDataset;
        } else {
            this.inputDataset = createInputDataSet(searchSourceBuilder, sourceIndices, dataFrame);
        }
    }

    private void validate(FunctionName algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm can't be null");
        }
    }

    public MLInput(StreamInput in) throws IOException {
        this.algorithm = in.readEnum(FunctionName.class);
        if (in.readBoolean()) {
            this.parameters = MLCommonsClassLoader.initMLInstance(algorithm, in, StreamInput.class);
        }
        if (in.readBoolean()) {
            this.inputDataset = MLInputDataset.fromStream(in);
        }
        this.version = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(algorithm);
        if (parameters != null) {
            out.writeBoolean(true);
            parameters.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (inputDataset != null) {
            out.writeBoolean(true);
            inputDataset.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeInt(version);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ALGORITHM_FIELD, algorithm.name());
        if (parameters != null) {
            builder.field(ML_PARAMETERS_FIELD, parameters);
        }
        if (inputDataset != null) {
            switch (inputDataset.getInputDataType()) {
                case SEARCH_QUERY:
                    builder.field(INPUT_INDEX_FIELD, ((SearchQueryInputDataset) inputDataset).getIndices().toArray(new String[0]));
                    builder.field(INPUT_QUERY_FIELD, ((SearchQueryInputDataset) inputDataset).getSearchSourceBuilder());
                    break;
                case DATA_FRAME:
                    builder.startObject(INPUT_DATA_FIELD);
                    ((DataFrameInputDataset) inputDataset).getDataFrame().toXContent(builder, EMPTY_PARAMS);
                    builder.endObject();
                    break;
                case TEXT_DOCS:
                    TextDocsInputDataSet textInputDataSet = (TextDocsInputDataSet) this.inputDataset;
                    List<String> docs = textInputDataSet.getDocs();
                    ModelResultFilter resultFilter = textInputDataSet.getResultFilter();
                    if (docs != null && docs.size() > 0) {
                        builder.field(TEXT_DOCS_FIELD, docs.toArray(new String[0]));
                    }
                    if (resultFilter != null) {
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
                    }
                default:
                    break;
            }

        }
        builder.endObject();
        return builder;
    }

    public static MLInput parse(XContentParser parser, String inputAlgoName) throws IOException {
        String algorithmName = inputAlgoName.toUpperCase(Locale.ROOT);
        FunctionName algorithm = FunctionName.from(algorithmName);

        if (MLCommonsClassLoader.canInitMLInput(algorithm)) {
            MLInput mlInput = MLCommonsClassLoader
                .initMLInput(algorithm, new Object[] { parser, algorithm }, XContentParser.class, FunctionName.class);
            mlInput.setAlgorithm(algorithm);
            return mlInput;
        }

        MLAlgoParams mlParameters = null;
        SearchSourceBuilder searchSourceBuilder = null;
        List<String> sourceIndices = new ArrayList<>();
        DataFrame dataFrame = null;

        boolean returnBytes = false;
        boolean returnNumber = true;
        List<String> targetResponse = new ArrayList<>();
        List<Integer> targetResponsePositions = new ArrayList<>();
        List<String> textDocs = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ML_PARAMETERS_FIELD:
                    mlParameters = parser.namedObject(MLAlgoParams.class, algorithmName, null);
                    break;
                case INPUT_INDEX_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        sourceIndices.add(parser.text());
                    }
                    break;
                case INPUT_QUERY_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
                    searchSourceBuilder = SearchSourceBuilder.fromXContent(parser, false);
                    break;
                case INPUT_DATA_FIELD:
                    dataFrame = DefaultDataFrame.parse(parser);
                    break;
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
                        textDocs.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        MLInputDataset inputDataSet = null;
        if (algorithm == FunctionName.TEXT_EMBEDDING
            || algorithm == FunctionName.SPARSE_ENCODING
            || algorithm == FunctionName.SPARSE_TOKENIZE) {
            ModelResultFilter filter = new ModelResultFilter(returnBytes, returnNumber, targetResponse, targetResponsePositions);
            inputDataSet = new TextDocsInputDataSet(textDocs, filter);
        }
        return new MLInput(algorithm, mlParameters, searchSourceBuilder, sourceIndices, dataFrame, inputDataSet);
    }

    private MLInputDataset createInputDataSet(SearchSourceBuilder searchSourceBuilder, List<String> sourceIndices, DataFrame dataFrame) {
        if (dataFrame != null) {
            return new DataFrameInputDataset(dataFrame);
        }
        if (sourceIndices != null && searchSourceBuilder != null) {
            return new SearchQueryInputDataset(sourceIndices, searchSourceBuilder);
        }
        return null;
    }

    @Override
    public FunctionName getFunctionName() {
        return this.algorithm;
    }

}

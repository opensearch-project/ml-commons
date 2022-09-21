/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLModelResultFilter;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextInputDataSet;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelTaskType;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * ML input data: algirithm name, parameters and input data set.
 */
@Data
public class MLInput implements Input {

    public static final String ALGORITHM_FIELD = "algorithm";
    public static final String ML_PARAMETERS_FIELD = "parameters";
    public static final String INPUT_INDEX_FIELD = "input_index";
    public static final String INPUT_QUERY_FIELD = "input_query";
    public static final String INPUT_DATA_FIELD = "input_data";
    
    public static final String MODEL_TASK_TYPE_FIELD = "model_task_type";
    public static final String RETURN_BYTES_FIELD = "return_bytes";
    public static final String RETURN_NUMBER_FIELD = "return_number";
    public static final String TARGET_RESPONSE_FIELD = "target_response";
    public static final String TARGET_RESPONSE_POSITIONS_FIELD = "target_response_positions";
    public static final String TEXT_FIELD = "text";

    // Algorithm name
    private FunctionName algorithm;
    // ML algorithm parameters
    private MLAlgoParams parameters;
    // Input data to train model, run trained model to predict or run ML algorithms(no-model-based) directly.
    private MLInputDataset inputDataset;
    // Model task type
    private MLModelTaskType mlModelTaskType;

    private int version = 1;

    @Builder(toBuilder = true)
    public MLInput(FunctionName algorithm, MLAlgoParams parameters, MLInputDataset inputDataset, MLModelTaskType mlModelTaskType) {
        validate(algorithm);
        this.algorithm = algorithm;
        this.parameters = parameters;
        this.inputDataset = inputDataset;
        this.mlModelTaskType = mlModelTaskType;
    }

    public MLInput(FunctionName algorithm, MLAlgoParams parameters, SearchSourceBuilder searchSourceBuilder,
                   List<String> sourceIndices, DataFrame dataFrame, MLInputDataset inputDataset,
                   MLModelTaskType mlModelTaskType) {
        validate(algorithm);
        this.algorithm = algorithm;
        this.parameters = parameters;
        if (inputDataset != null) {
            this.inputDataset = inputDataset;
        } else {
            this.inputDataset = createInputDataSet(searchSourceBuilder, sourceIndices, dataFrame);
        }
        this.mlModelTaskType = mlModelTaskType;
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
            MLInputDataType inputDataType = in.readEnum(MLInputDataType.class);
            this.inputDataset = MLCommonsClassLoader.initMLInstance(inputDataType, in, StreamInput.class);
        }
        this.version = in.readInt();
        if (in.available() > 0 && in.readBoolean()) {
            this.mlModelTaskType = in.readEnum(MLModelTaskType.class);
        }
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
        if (mlModelTaskType != null) {
            out.writeBoolean(true);
            out.writeEnum(mlModelTaskType);
        } else {
            out.writeBoolean(false);
        }
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
                    builder.field(INPUT_INDEX_FIELD, ((SearchQueryInputDataset)inputDataset).getIndices().toArray(new String[0]));
                    builder.field(INPUT_QUERY_FIELD, ((SearchQueryInputDataset)inputDataset).getSearchSourceBuilder());
                    break;
                case DATA_FRAME:
                    builder.startObject(INPUT_DATA_FIELD);
                    ((DataFrameInputDataset)inputDataset).getDataFrame().toXContent(builder, EMPTY_PARAMS);
                    builder.endObject();
                    break;
                    //TODO: add text docs input
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
        MLAlgoParams mlParameters = null;
        SearchSourceBuilder searchSourceBuilder = null;
        List<String> sourceIndices = new ArrayList<>();
        DataFrame dataFrame = null;

        MLModelTaskType modelTaskType = MLModelTaskType.TEXT_EMBEDDING;
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
                case MODEL_TASK_TYPE_FIELD:
                    modelTaskType = MLModelTaskType.from(parser.text().toUpperCase(Locale.ROOT));
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
                case TEXT_FIELD://TODO: support query docs from index directly
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
        MLModelTaskType mlModelTaskType = null;
        MLInputDataset inputDataSet = null;
        if (algorithm == FunctionName.CUSTOM) {
            if (modelTaskType == null) {
                throw new IllegalArgumentException("modle task type not set");
            }
            switch (modelTaskType) {
                case TEXT_EMBEDDING:
                    MLModelResultFilter filter = new MLModelResultFilter(returnBytes, returnNumber, targetResponse, targetResponsePositions);
                    inputDataSet = new TextInputDataSet(textDocs, filter);
                    mlModelTaskType = MLModelTaskType.TEXT_EMBEDDING;
                    break;
                default:
                    throw new IllegalArgumentException("unknown modle task type");
            }
        }
        return new MLInput(algorithm, mlParameters, searchSourceBuilder, sourceIndices, dataFrame, inputDataSet, mlModelTaskType);
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

    public DataFrame getDataFrame() {
        if (inputDataset == null || !(inputDataset instanceof DataFrameInputDataset)) {
            return null;
        }
        return ((DataFrameInputDataset)inputDataset).getDataFrame();
    }

}

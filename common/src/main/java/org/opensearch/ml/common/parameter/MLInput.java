/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    // Algorithm name
    private MLAlgoName algorithm;
    // ML algorithm parameters
    private MLAlgoParams parameters;
    // Input data to train model, run trained model to predict or run ML algorithms(no-model-based) directly.
    private MLInputDataset inputDataset;

    private int version = 1;

    @Builder
    public MLInput(MLAlgoName algorithm, MLAlgoParams parameters, SearchSourceBuilder searchSourceBuilder, List<String> sourceIndices, DataFrame dataFrame, MLInputDataset inputDataset) {
        this.algorithm = algorithm;
        this.parameters = parameters;
        if (inputDataset != null) {
            this.inputDataset = inputDataset;
        } else {
            this.inputDataset = createInputDataSet(searchSourceBuilder, sourceIndices, dataFrame);
        }
    }

    public MLInput(StreamInput in) throws IOException {
        this.algorithm = in.readEnum(MLAlgoName.class);
        if (in.readBoolean()) {
            this.parameters = MLCommonsClassLoader.initInstance(algorithm, in, StreamInput.class);
        }
        if (in.readBoolean()) {
            MLInputDataType inputDataType = in.readEnum(MLInputDataType.class);
            this.inputDataset = MLCommonsClassLoader.initInstance(inputDataType, in, StreamInput.class);
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
        builder.field(ALGORITHM_FIELD, algorithm.getName());
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
                    builder.field(INPUT_DATA_FIELD, (ToXContent) ((DataFrameInputDataset)inputDataset).getDataFrame());
                    break;
                default:
                    break;
            }

        }
        return builder;
    }

    public static MLInput parse(XContentParser parser, String algorithmName) throws IOException {
        MLAlgoName algorithm = MLAlgoName.fromString(algorithmName);
        MLAlgoParams mlParameters = null;
        SearchSourceBuilder searchSourceBuilder = null;
        List<String> sourceIndices = new ArrayList<>();
        DataFrame dataFrame = null;

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
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLInput(algorithm, mlParameters, searchSourceBuilder, sourceIndices, dataFrame, null);
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
    public MLAlgoName getFunctionName() {
        return this.algorithm;
    }
}

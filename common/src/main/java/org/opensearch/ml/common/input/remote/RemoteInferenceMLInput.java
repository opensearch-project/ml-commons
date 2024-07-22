/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.remote;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.PredictMode;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.utils.StringUtils;

import java.io.IOException;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@org.opensearch.ml.common.annotation.MLInput(functionNames = {FunctionName.REMOTE})
public class RemoteInferenceMLInput extends MLInput {
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String PREDICT_MODE_FIELD = "mode";

    public RemoteInferenceMLInput(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    public RemoteInferenceMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        super();
        this.algorithm = functionName;
        Map<String, String> parameters = null;
        PredictMode predictMode = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case PARAMETERS_FIELD:
                    parameters = StringUtils.getParameterMap(parser.map());
                    break;
                case PREDICT_MODE_FIELD:
                    predictMode = PredictMode.from(parser.text());
                default:
                    parser.skipChildren();
                    break;
            }
        }
        predictMode = predictMode == null? PredictMode.PREDICT:predictMode;
        inputDataset = new RemoteInferenceInputDataSet(parameters, predictMode);
    }

}

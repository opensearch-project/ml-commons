/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.remote;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.utils.StringUtils;

@org.opensearch.ml.common.annotation.MLInput(functionNames = { FunctionName.REMOTE })
public class RemoteInferenceMLInput extends MLInput {
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String ACTION_TYPE_FIELD = "action_type";
    public static final String DLQ_FIELD = "dlq";

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
        Map<String, String> dlq = null;
        ActionType actionType = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case PARAMETERS_FIELD:
                    parameters = StringUtils.getParameterMap(parser.map());
                    break;
                case ACTION_TYPE_FIELD:
                    actionType = ActionType.from(parser.text());
                    break;
                case DLQ_FIELD:
                    dlq = StringUtils.getParameterMap(parser.map());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        inputDataset = new RemoteInferenceInputDataSet(parameters, actionType, dlq);
    }

}

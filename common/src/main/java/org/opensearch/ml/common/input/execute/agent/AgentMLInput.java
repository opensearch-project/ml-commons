/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.utils.StringUtils;

import java.io.IOException;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;


@org.opensearch.ml.common.annotation.MLInput(functionNames = {FunctionName.AGENT})
public class AgentMLInput extends MLInput {
    public static final String AGENT_ID_FIELD = "agent_id";
    public static final String PARAMETERS_FIELD = "parameters";

    @Getter @Setter
    private String agentId;

    @Getter @Setter
    private String tenantId;


    @Builder(builderMethodName = "AgentMLInputBuilder")
    public AgentMLInput(String agentId, String tenantId, FunctionName functionName, MLInputDataset inputDataset) {
        this.agentId = agentId;
        this.tenantId = tenantId;
        this.algorithm = functionName;
        this.inputDataset = inputDataset;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(agentId);
        // TODO: need to check BWC later
        out.writeOptionalString(tenantId);
    }

    public AgentMLInput(StreamInput in) throws IOException {
        super(in);
        this.agentId = in.readString();
        //TODO: need to check BWC later
        this.tenantId = in.readOptionalString();
    }

    public AgentMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        super();
        this.algorithm = functionName;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case AGENT_ID_FIELD:
                    agentId = parser.text();
                    break;
                case TENANT_ID:
                    tenantId = parser.textOrNull();
                    break;
                case PARAMETERS_FIELD:
                    Map<String, String> parameters = StringUtils.getParameterMap(parser.map());
                    inputDataset = new RemoteInferenceInputDataSet(parameters);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
    }

}

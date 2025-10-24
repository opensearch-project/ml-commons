/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.CommonValue.VERSION_3_3_0;

import java.io.IOException;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.AgentInput;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@org.opensearch.ml.common.annotation.MLInput(functionNames = { FunctionName.AGENT })
public class AgentMLInput extends MLInput {
    public static final String AGENT_ID_FIELD = "agent_id";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String INPUT_FIELD = "input";
    public static final String ASYNC_FIELD = "isAsync";

    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_ASYNC_EXECUTION = CommonValue.VERSION_3_0_0;

    @Getter
    @Setter
    private String agentId;

    @Getter
    @Setter
    private String tenantId;

    @Getter
    @Setter
    private Boolean isAsync;

    @Getter
    @Setter
    private AgentInput agentInput;

    @Builder(builderMethodName = "AgentMLInputBuilder")
    public AgentMLInput(String agentId, String tenantId, FunctionName functionName, MLInputDataset inputDataset) {
        this(agentId, tenantId, functionName, inputDataset, false);
    }

    @Builder(builderMethodName = "AgentMLInputBuilder")
    public AgentMLInput(String agentId, String tenantId, FunctionName functionName, MLInputDataset inputDataset, Boolean isAsync) {
        this.agentId = agentId;
        this.tenantId = tenantId;
        this.algorithm = functionName;
        this.inputDataset = inputDataset;
        this.isAsync = isAsync;
        this.agentInput = null; // Legacy constructor - no standardized input
    }

    // New constructor for standardized input
    @Builder(builderMethodName = "AgentMLInputBuilderWithStandardInput")
    public AgentMLInput(
        String agentId,
        String tenantId,
        FunctionName functionName,
        AgentInput agentInput,
        MLInputDataset inputDataset,
        Boolean isAsync
    ) {
        this.agentId = agentId;
        this.tenantId = tenantId;
        this.algorithm = functionName;
        this.agentInput = agentInput;
        this.inputDataset = inputDataset;
        this.isAsync = isAsync;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        Version streamOutputVersion = out.getVersion();
        out.writeString(agentId);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
        if (streamOutputVersion.onOrAfter(AgentMLInput.MINIMAL_SUPPORTED_VERSION_FOR_ASYNC_EXECUTION)) {
            out.writeOptionalBoolean(isAsync);
        }
        // Todo: finalize the version
        if (streamOutputVersion.onOrAfter(VERSION_3_3_0)) {
            out.writeBoolean(agentInput != null);
            if (agentInput != null) {
                agentInput.writeTo(out);
            }
        }
    }

    public AgentMLInput(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.agentId = in.readString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
        if (streamInputVersion.onOrAfter(AgentMLInput.MINIMAL_SUPPORTED_VERSION_FOR_ASYNC_EXECUTION)) {
            this.isAsync = in.readOptionalBoolean();
        }
        if (streamInputVersion.onOrAfter(VERSION_3_3_0)) {
            if (in.readBoolean()) {
                this.agentInput = new AgentInput(in);
            }
        }
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
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                case PARAMETERS_FIELD:
                    // Legacy format - parse parameters into RemoteInferenceInputDataSet
                    Map<String, String> parameters = StringUtils.getParameterMap(parser.map());
                    inputDataset = new RemoteInferenceInputDataSet(parameters);
                    break;
                case INPUT_FIELD:
                    agentInput = new AgentInput(parser);
                    break;
                case ASYNC_FIELD:
                    isAsync = parser.booleanValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
    }

    /**
     * Checks if this AgentMLInput uses the new standardized input format.
     * @return true if AgentInput is present
     */
    public boolean hasStandardInput() {
        return agentInput != null;
    }
}

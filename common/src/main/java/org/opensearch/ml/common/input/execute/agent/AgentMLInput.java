/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.agent.MLAgent.CONTEXT_MANAGEMENT_NAME_FIELD;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.hooks.HookRegistry;
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
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_AGENT_INTERFACE_REVAMP = CommonValue.VERSION_3_5_0;

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
    private HookRegistry hookRegistry;

    @Getter
    @Setter
    private String contextManagementName;

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
        // Note: contextManagementName and hookRegistry are not serialized as they are runtime-only fields
        if (streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_AGENT_INTERFACE_REVAMP)) {
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
        if (streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_AGENT_INTERFACE_REVAMP)) {
            if (in.readBoolean()) {
                this.agentInput = new AgentInput(in);
            }
        }
    }

    public AgentMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        super();
        this.algorithm = functionName;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        String topLevelContextManagementName = null;
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
                    Map<String, Object> parameterObjs = parser.map();
                    Map<String, String> parameters = StringUtils.getParameterMap(parameterObjs);
                    // Extract context_management from parameters
                    if (parameterObjs.containsKey(CONTEXT_MANAGEMENT_NAME_FIELD)) {
                        contextManagementName = (String) parameterObjs.get(CONTEXT_MANAGEMENT_NAME_FIELD);
                    }
                    inputDataset = new RemoteInferenceInputDataSet(parameters);
                    break;
                case INPUT_FIELD:
                    agentInput = new AgentInput(parser);
                    break;
                case ASYNC_FIELD:
                    isAsync = parser.booleanValue();
                    break;
                case CONTEXT_MANAGEMENT_NAME_FIELD:
                    if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                        topLevelContextManagementName = null;
                    } else if (parser.currentToken() == XContentParser.Token.VALUE_STRING) {
                        topLevelContextManagementName = parser.text();
                    } else {
                        throw new IllegalArgumentException(CONTEXT_MANAGEMENT_NAME_FIELD + " must be a string");
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        applyTopLevelContextManagementName(topLevelContextManagementName);
    }

    /**
     * Agent registration takes context_management_name as a top-level field, so execute accepts it there too, next to
     * parameters.context_management_name. A value inside parameters wins. contextManagementName is not serialized, so the
     * name is also copied into the parameters: after the request is sent to another node it is then handled exactly like
     * parameters.context_management_name.
     */
    private void applyTopLevelContextManagementName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (inputDataset == null) {
            if (agentInput == null) {
                // No parameters and no input: leave the dataset unset so the request still fails as empty.
                return;
            }
            inputDataset = new RemoteInferenceInputDataSet(new HashMap<>());
        }
        if (!(inputDataset instanceof RemoteInferenceInputDataSet)) {
            return;
        }
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) inputDataset;
        if (dataset.getParameters() == null) {
            dataset.setParameters(new HashMap<>());
        }
        String fromParameters = dataset.getParameters().get(CONTEXT_MANAGEMENT_NAME_FIELD);
        if (fromParameters == null || fromParameters.isBlank()) {
            dataset.getParameters().put(CONTEXT_MANAGEMENT_NAME_FIELD, name);
            contextManagementName = name;
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

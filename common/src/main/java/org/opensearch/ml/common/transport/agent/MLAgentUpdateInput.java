/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
public class MLAgentUpdateInput implements ToXContentObject, Writeable {

    public static final String AGENT_ID_FIELD = "agent_id";
    public static final String AGENT_NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String LLM_FIELD = "llm";
    public static final String LLM_MODEL_ID_FIELD = "model_id";
    public static final String LLM_PARAMETERS_FIELD = "parameters";
    public static final String TOOLS_FIELD = "tools";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String MEMORY_FIELD = "memory";
    public static final String MEMORY_TYPE_FIELD = "type";
    public static final String MEMORY_SESSION_ID_FIELD = "session_id";
    public static final String MEMORY_WINDOW_SIZE_FIELD = "window_size";
    public static final String APP_TYPE_FIELD = "app_type";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";

    @Getter
    private String agentId;
    private String name;
    private String description;
    private String llmModelId;
    private Map<String, String> llmParameters;
    private List<MLToolSpec> tools;
    private Map<String, String> parameters;
    private String memoryType;
    private String memorySessionId;
    private Integer memoryWindowSize;
    private String appType;
    private Instant lastUpdateTime;
    private String tenantId;

    @Builder(toBuilder = true)
    public MLAgentUpdateInput(
        String agentId,
        String name,
        String description,
        String llmModelId,
        Map<String, String> llmParameters,
        List<MLToolSpec> tools,
        Map<String, String> parameters,
        String memoryType,
        String memorySessionId,
        Integer memoryWindowSize,
        String appType,
        Instant lastUpdateTime,
        String tenantId
    ) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.llmModelId = llmModelId;
        this.llmParameters = llmParameters;
        this.tools = tools;
        this.parameters = parameters;
        this.memoryType = memoryType;
        this.memorySessionId = memorySessionId;
        this.memoryWindowSize = memoryWindowSize;
        this.appType = appType;
        this.lastUpdateTime = lastUpdateTime;
        this.tenantId = tenantId;
        validate();
    }

    public MLAgentUpdateInput(StreamInput in) throws IOException {
        Version streamInputVersion = in.getVersion();
        agentId = in.readString();
        name = in.readOptionalString();
        description = in.readOptionalString();
        llmModelId = in.readOptionalString();
        if (in.readBoolean()) {
            llmParameters = in.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        if (in.readBoolean()) {
            tools = new ArrayList<>();
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                tools.add(new MLToolSpec(in));
            }
        }
        if (in.readBoolean()) {
            parameters = in.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        memoryType = in.readOptionalString();
        memorySessionId = in.readOptionalString();
        memoryWindowSize = in.readOptionalInt();
        lastUpdateTime = in.readOptionalInstant();
        appType = in.readOptionalString();
        tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(AGENT_ID_FIELD, agentId);
        if (name != null) {
            builder.field(AGENT_NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (llmModelId != null || (llmParameters != null && !llmParameters.isEmpty())) {
            builder.startObject(LLM_FIELD);
            if (llmModelId != null) {
                builder.field(LLM_MODEL_ID_FIELD, llmModelId);
            }
            if (llmParameters != null && !llmParameters.isEmpty()) {
                builder.field(LLM_PARAMETERS_FIELD, llmParameters);
            }
            builder.endObject();
        }
        if (tools != null && !tools.isEmpty()) {
            builder.field(TOOLS_FIELD, tools);
        }
        if (parameters != null && !parameters.isEmpty()) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (memoryType != null || memorySessionId != null || memoryWindowSize != null) {
            builder.startObject(MEMORY_FIELD);
            if (memoryType != null) {
                builder.field(MEMORY_TYPE_FIELD, memoryType);
            }
            if (memorySessionId != null) {
                builder.field(MEMORY_SESSION_ID_FIELD, memorySessionId);
            }
            if (memoryWindowSize != null) {
                builder.field(MEMORY_WINDOW_SIZE_FIELD, memoryWindowSize);
            }
            builder.endObject();
        }
        if (appType != null) {
            builder.field(APP_TYPE_FIELD, appType);
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeString(agentId);
        out.writeOptionalString(name);
        out.writeOptionalString(description);
        out.writeOptionalString(llmModelId);
        if (llmParameters != null && !llmParameters.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(llmParameters, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        if (tools != null && !tools.isEmpty()) {
            out.writeBoolean(true);
            out.writeInt(tools.size());
            for (MLToolSpec tool : tools) {
                tool.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
        if (parameters != null && !parameters.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(memoryType);
        out.writeOptionalString(memorySessionId);
        out.writeOptionalInt(memoryWindowSize);
        out.writeOptionalInstant(lastUpdateTime);
        out.writeOptionalString(appType);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
    }

    public static MLAgentUpdateInput parse(XContentParser parser) throws IOException {
        String agentId = null;
        String name = null;
        String description = null;
        String llmModelId = null;
        Map<String, String> llmParameters = null;
        List<MLToolSpec> tools = null;
        Map<String, String> parameters = null;
        String memoryType = null;
        String memorySessionId = null;
        Integer memoryWindowSize = null;
        String appType = null;
        Instant lastUpdateTime = null;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case AGENT_ID_FIELD:
                    agentId = parser.text();
                    break;
                case AGENT_NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case LLM_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                        String llmFieldName = parser.currentName();
                        parser.nextToken();
                        switch (llmFieldName) {
                            case LLM_MODEL_ID_FIELD:
                                llmModelId = parser.text();
                                break;
                            case LLM_PARAMETERS_FIELD:
                                llmParameters = parser.mapStrings();
                                break;
                            default:
                                parser.skipChildren();
                                break;
                        }
                    }
                    break;
                case TOOLS_FIELD:
                    tools = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        tools.add(MLToolSpec.parse(parser));
                    }
                    break;
                case PARAMETERS_FIELD:
                    parameters = parser.mapStrings();
                    break;
                case MEMORY_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                        String memoryFieldName = parser.currentName();
                        parser.nextToken();
                        switch (memoryFieldName) {
                            case MEMORY_TYPE_FIELD:
                                memoryType = parser.text();
                                break;
                            case MEMORY_SESSION_ID_FIELD:
                                memorySessionId = parser.text();
                                break;
                            case MEMORY_WINDOW_SIZE_FIELD:
                                memoryWindowSize = parser.intValue();
                                break;
                            default:
                                parser.skipChildren();
                                break;
                        }
                    }
                    break;
                case APP_TYPE_FIELD:
                    appType = parser.text();
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new MLAgentUpdateInput(
            agentId,
            name,
            description,
            llmModelId,
            llmParameters,
            tools,
            parameters,
            memoryType,
            memorySessionId,
            memoryWindowSize,
            appType,
            lastUpdateTime,
            tenantId
        );
    }

    public MLAgent toMLAgent(MLAgent originalAgent) {
        LLMSpec finalLlm;
        if (llmModelId == null && (llmParameters == null || llmParameters.isEmpty())) {
            finalLlm = originalAgent.getLlm();
        } else {
            LLMSpec originalLlm = originalAgent.getLlm();

            String finalModelId = llmModelId != null ? llmModelId : originalLlm.getModelId();

            Map<String, String> finalParameters = new HashMap<>();
            if (originalLlm != null && originalLlm.getParameters() != null) {
                finalParameters.putAll(originalLlm.getParameters());
            }
            if (llmParameters != null) {
                finalParameters.putAll(llmParameters);
            }

            finalLlm = LLMSpec.builder().modelId(finalModelId).parameters(finalParameters).build();
        }

        MLMemorySpec finalMemory;
        if (memoryType == null && memorySessionId == null && memoryWindowSize == null) {
            finalMemory = originalAgent.getMemory();
        } else {
            MLMemorySpec originalMemory = originalAgent.getMemory();

            String finalMemoryType = memoryType != null ? memoryType : originalMemory.getType();
            String finalSessionId = memorySessionId != null ? memorySessionId : originalMemory.getSessionId();
            Integer finalWindowSize = memoryWindowSize != null ? memoryWindowSize : originalMemory.getWindowSize();

            finalMemory = MLMemorySpec.builder().type(finalMemoryType).sessionId(finalSessionId).windowSize(finalWindowSize).build();
        }

        return MLAgent
            .builder()
            .type(originalAgent.getType())
            .createdTime(originalAgent.getCreatedTime())
            .isHidden(originalAgent.getIsHidden())
            .name(name == null ? originalAgent.getName() : name)
            .description(description == null ? originalAgent.getDescription() : description)
            .llm(finalLlm)
            .tools(tools == null ? originalAgent.getTools() : tools)
            .parameters(parameters == null ? originalAgent.getParameters() : parameters)
            .memory(finalMemory)
            .lastUpdateTime(lastUpdateTime)
            .appType(appType)
            .tenantId(tenantId)
            .build();
    }

    private void validate() {
        if (name != null && (name.isBlank() || name.length() > MLAgent.AGENT_NAME_MAX_LENGTH)) {
            throw new IllegalArgumentException(
                String.format("Agent name cannot be empty or exceed max length of %d characters", MLAgent.AGENT_NAME_MAX_LENGTH)
            );
        }
        if (memoryType != null && !memoryType.equals("conversation_index")) {
            throw new IllegalArgumentException(String.format("Invalid memory type: %s", memoryType));
        }
        if (tools != null) {
            Set<String> toolNames = new HashSet<>();
            for (MLToolSpec toolSpec : tools) {
                String toolName = Optional.ofNullable(toolSpec.getName()).orElse(toolSpec.getType());
                if (toolNames.contains(toolName)) {
                    throw new IllegalArgumentException("Duplicate tool defined: " + toolName);
                } else {
                    toolNames.add(toolName);
                }
            }
        }
    }
}

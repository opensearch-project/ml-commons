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
    public static final String TOOLS_FIELD = "tools";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String MEMORY_FIELD = "memory";
    public static final String APP_TYPE_FIELD = "app_type";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";

    @Getter
    private String agentId;
    private String name;
    private String description;
    private LLMSpec llm;
    private List<MLToolSpec> tools;
    private Map<String, String> parameters;
    private MLMemorySpec memory;
    private String appType;
    private Instant lastUpdateTime;
    private String tenantId;

    @Builder(toBuilder = true)
    public MLAgentUpdateInput(
        String agentId,
        String name,
        String description,
        LLMSpec llm,
        List<MLToolSpec> tools,
        Map<String, String> parameters,
        MLMemorySpec memory,
        String appType,
        Instant lastUpdateTime,
        String tenantId
    ) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.llm = llm;
        this.tools = tools;
        this.parameters = parameters;
        this.memory = memory;
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
        if (in.readBoolean()) {
            llm = new LLMSpec(in);
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
        if (in.readBoolean()) {
            memory = new MLMemorySpec(in);
        }
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
        if (llm != null) {
            builder.field(LLM_FIELD, llm);
        }
        if (tools != null && !tools.isEmpty()) {
            builder.field(TOOLS_FIELD, tools);
        }
        if (parameters != null && !parameters.isEmpty()) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (memory != null) {
            builder.field(MEMORY_FIELD, memory);
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
        if (llm != null) {
            out.writeBoolean(true);
            llm.writeTo(out);
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
        if (memory != null) {
            out.writeBoolean(true);
            memory.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
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
        LLMSpec llm = null;
        List<MLToolSpec> tools = null;
        Map<String, String> parameters = null;
        MLMemorySpec memory = null;
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
                    llm = LLMSpec.parse(parser);
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
                    memory = MLMemorySpec.parse(parser);
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

        return new MLAgentUpdateInput(agentId, name, description, llm, tools, parameters, memory, appType, lastUpdateTime, tenantId);
    }

    public MLAgent toMLAgent(MLAgent originalAgent) {
        return MLAgent
            .builder()
            .type(originalAgent.getType())
            .createdTime(originalAgent.getCreatedTime())
            .isHidden(originalAgent.getIsHidden())
            .name(name == null ? originalAgent.getName() : name)
            .description(description == null ? originalAgent.getDescription() : description)
            .llm(llm == null ? originalAgent.getLlm() : llm)
            .tools(tools == null ? originalAgent.getTools() : tools)
            .parameters(parameters == null ? originalAgent.getParameters() : parameters)
            .memory(memory == null ? originalAgent.getMemory() : memory)
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
        if (memory != null && !memory.getType().equals("conversation_index")) {
            throw new IllegalArgumentException(String.format("Invalid memory type: %s", memory.getType()));
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

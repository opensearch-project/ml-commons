/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;


@Getter
public class MLAgent implements ToXContentObject, Writeable {
    public static final String AGENT_NAME_FIELD = "name";
    public static final String AGENT_TYPE_FIELD = "type";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String LLM_FIELD = "llm";
    public static final String TOOLS_FIELD = "tools";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String MEMORY_FIELD = "memory";
    public static final String MEMORY_ID_FIELD = "memory_id";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String APP_TYPE_FIELD = "app_type";

    private String name;
    private String type;
    private String description;
    private LLMSpec llm;
    private List<MLToolSpec> tools;
    private Map<String, String> parameters;
    private MLMemorySpec memory;

    private Instant createdTime;
    private Instant lastUpdateTime;
    private String appType;

    @Builder(toBuilder = true)
    public MLAgent(String name,
                   String type,
                   String description,
                   LLMSpec llm,
                   List<MLToolSpec> tools,
                   Map<String, String> parameters,
                   MLMemorySpec memory,
                   Instant createdTime,
                   Instant lastUpdateTime,
                   String appType) {
        if (name == null) {
            throw new IllegalArgumentException("agent name is null");
        }
        this.name = name;
        this.type = type;
        this.description = description;
        this.llm = llm;
        this.tools = tools;
        this.parameters = parameters;
        this.memory = memory;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
        this.appType = appType;
    }

    public MLAgent(StreamInput input) throws IOException{
        name = input.readString();
        type = input.readString();
        description = input.readOptionalString();
        if (input.readBoolean()) {
            llm = new LLMSpec(input);
        }
        if (input.readBoolean()) {
            tools = new ArrayList<>();
            int size = input.readInt();
            for (int i=0; i<size; i++) {
                tools.add(new MLToolSpec(input));
            }
        }
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        if (input.readBoolean()) {
            memory = new MLMemorySpec(input);
        }
        createdTime = input.readInstant();
        lastUpdateTime = input.readInstant();
        appType = input.readString();
        if (!"flow".equals(type)) {
            Set<String> toolNames = new HashSet<>();
            for (MLToolSpec toolSpec : tools) {
                String toolName = Optional.ofNullable(toolSpec.getName()).orElse(toolSpec.getType());
                if (toolNames.contains(toolName)) {
                    throw new IllegalArgumentException("Tool has duplicate name or alias: " + toolName);
                }
            }
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeString(type);
        out.writeOptionalString(description);
        if (llm != null) {
            out.writeBoolean(true);
            llm.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (tools != null && tools.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(tools.size());
            for (MLToolSpec tool : tools) {
                tool.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
        if (parameters != null && parameters.size() > 0) {
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
        out.writeInstant(createdTime);
        out.writeInstant(lastUpdateTime);
        out.writeString(appType);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(AGENT_NAME_FIELD, name);
        }
        if (type != null) {
            builder.field(AGENT_TYPE_FIELD, type);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (llm != null) {
            builder.field(LLM_FIELD, llm);
        }
        if (tools != null && tools.size() > 0) {
            builder.field(TOOLS_FIELD, tools);
        }
        if (parameters != null && parameters.size() > 0) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (memory != null) {
            builder.field(MEMORY_FIELD, memory);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (appType != null) {
            builder.field(APP_TYPE_FIELD, appType);
        }
        builder.endObject();
        return builder;
    }

    public static MLAgent parse(XContentParser parser) throws IOException {
        String name = null;
        String type = null;
        String description = null;;
        LLMSpec llm = null;
        List<MLToolSpec> tools = null;
        Map<String, String> parameters = null;
        MLMemorySpec memory = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        String appType = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case AGENT_NAME_FIELD:
                    name = parser.text();
                    break;
                case AGENT_TYPE_FIELD:
                    type = parser.text();
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
                    parameters = getParameterMap(parser.map());
                    break;
                case MEMORY_FIELD:
                    memory = MLMemorySpec.parse(parser);
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case APP_TYPE_FIELD:
                    appType = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLAgent.builder()
                .name(name)
                .type(type)
                .description(description)
                .llm(llm)
                .tools(tools)
                .parameters(parameters)
                .memory(memory)
                .createdTime(createdTime)
                .lastUpdateTime(lastUpdateTime)
                .appType(appType)
                .build();
    }

    public static MLAgent fromStream(StreamInput in) throws IOException {
        MLAgent agent = new MLAgent(in);
        return agent;
    }
}
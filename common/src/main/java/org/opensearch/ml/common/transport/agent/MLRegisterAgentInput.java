/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;

import lombok.Builder;
import lombok.Data;

/**
 * Input for simplified agent registration that supports both legacy and new formats
 */
@Data
public class MLRegisterAgentInput implements ToXContentObject, Writeable {

    public static final String NAME_FIELD = "name";
    public static final String TYPE_FIELD = "type";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String MODEL_FIELD = "model";
    public static final String TOOLS_FIELD = "tools";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String MEMORY_FIELD = "memory";
    
    // Legacy fields for backward compatibility
    public static final String LLM_FIELD = "llm";

    private String name;
    private String type;
    private String description;
    private MLAgentModelSpec model; // New simplified model spec
    private List<MLToolSpec> tools;
    private Map<String, String> parameters;
    private MLMemorySpec memory;
    
    // Legacy support
    private String legacyModelId; // For backward compatibility with existing LLM spec

    @Builder(toBuilder = true)
    public MLRegisterAgentInput(
        String name,
        String type,
        String description,
        MLAgentModelSpec model,
        List<MLToolSpec> tools,
        Map<String, String> parameters,
        MLMemorySpec memory,
        String legacyModelId
    ) {
        this.name = name;
        this.type = type != null ? type : "conversational"; // Default to conversational
        this.description = description;
        this.model = model;
        this.tools = tools;
        this.parameters = parameters;
        this.memory = memory;
        this.legacyModelId = legacyModelId;
    }

    public MLRegisterAgentInput(StreamInput in) throws IOException {
        name = in.readOptionalString();
        type = in.readOptionalString();
        description = in.readOptionalString();
        if (in.readBoolean()) {
            model = new MLAgentModelSpec(in);
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
        legacyModelId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        out.writeOptionalString(type);
        out.writeOptionalString(description);
        if (model != null) {
            out.writeBoolean(true);
            model.writeTo(out);
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
        out.writeOptionalString(legacyModelId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (type != null) {
            builder.field(TYPE_FIELD, type);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (model != null) {
            builder.field(MODEL_FIELD, model);
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
        builder.endObject();
        return builder;
    }

    public static MLRegisterAgentInput parse(XContentParser parser) throws IOException {
        String name = null;
        String type = null;
        String description = null;
        MLAgentModelSpec model = null;
        List<MLToolSpec> tools = null;
        Map<String, String> parameters = null;
        MLMemorySpec memory = null;
        String legacyModelId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case MODEL_FIELD:
                    model = MLAgentModelSpec.parse(parser);
                    break;
                case LLM_FIELD:
                    // Legacy support - extract model_id for backward compatibility
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                        String llmFieldName = parser.currentName();
                        parser.nextToken();
                        if ("model_id".equals(llmFieldName)) {
                            legacyModelId = parser.text();
                        } else {
                            parser.skipChildren();
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
                    parameters = getParameterMap(parser.map());
                    break;
                case MEMORY_FIELD:
                    memory = MLMemorySpec.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLRegisterAgentInput.builder()
            .name(name)
            .type(type)
            .description(description)
            .model(model)
            .tools(tools)
            .parameters(parameters)
            .memory(memory)
            .legacyModelId(legacyModelId)
            .build();
    }
}
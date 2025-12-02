/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.CommonValue.VERSION_3_0_0;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode
@Getter
public class MLToolSpec implements ToXContentObject {
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_TOOL_CONFIG = CommonValue.VERSION_2_18_0;

    public static final String TOOL_TYPE_FIELD = "type";
    public static final String TOOL_NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String ATTRIBUTES_FIELD = "attributes";
    public static final String INCLUDE_OUTPUT_IN_AGENT_RESPONSE = "include_output_in_agent_response";
    public static final String CONFIG_FIELD = "config";
    public static final String RUN_TIME_RESOURCES_FIELD = "runtime_resources";

    private String type;
    private String name;
    private String description;
    private Map<String, String> parameters;
    private Map<String, String> attributes;
    private boolean includeOutputInAgentResponse;
    private Map<String, String> configMap;
    @Setter
    private String tenantId;
    private Map<String, Object> runtimeResources;

    @Builder(toBuilder = true)
    public MLToolSpec(
        String type,
        String name,
        String description,
        Map<String, String> parameters,
        Map<String, String> attributes,
        boolean includeOutputInAgentResponse,
        Map<String, String> configMap,
        String tenantId,
        Map<String, Object> runtimeResources
    ) {
        if (type == null) {
            throw new IllegalArgumentException("tool type is null");
        }
        this.type = type;
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.attributes = attributes;
        this.includeOutputInAgentResponse = includeOutputInAgentResponse;
        this.configMap = configMap;
        this.tenantId = tenantId;
        this.runtimeResources = runtimeResources;
    }

    public MLToolSpec(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        type = input.readString();
        name = input.readOptionalString();
        description = input.readOptionalString();
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        includeOutputInAgentResponse = input.readBoolean();
        if (input.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_TOOL_CONFIG) && input.readBoolean()) {
            configMap = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
        if (input.getVersion().onOrAfter(VERSION_3_0_0)) {
            if (input.available() > 0 && input.readBoolean()) {
                attributes = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
            }
            if (input.available() > 0 && input.readBoolean()) {
                runtimeResources = input.readMap(StreamInput::readString, StreamInput::readGenericValue);
            }
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeString(type);
        out.writeOptionalString(name);
        out.writeOptionalString(description);
        if (parameters != null && !parameters.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(includeOutputInAgentResponse);
        if (out.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_TOOL_CONFIG)) {
            if (configMap != null) {
                out.writeBoolean(true);
                out.writeMap(configMap, StreamOutput::writeString, StreamOutput::writeOptionalString);
            } else {
                out.writeBoolean(false);
            }
        }
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
        if (streamOutputVersion.onOrAfter(VERSION_3_0_0)) {
            if (attributes != null && !attributes.isEmpty()) {
                out.writeBoolean(true);
                out.writeMap(attributes, StreamOutput::writeString, StreamOutput::writeOptionalString);
            } else {
                out.writeBoolean(false);
            }
            if (runtimeResources != null && !runtimeResources.isEmpty()) {
                out.writeBoolean(true);
                out.writeMap(runtimeResources, StreamOutput::writeString, StreamOutput::writeGenericValue);
            } else {
                out.writeBoolean(false);
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (type != null) {
            builder.field(TOOL_TYPE_FIELD, type);
        }
        if (name != null && !name.isEmpty()) {
            builder.field(TOOL_NAME_FIELD, name);
        }
        if (description != null && !description.isEmpty()) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (attributes != null && !attributes.isEmpty()) {
            builder.field(ATTRIBUTES_FIELD, attributes);
        }
        if (parameters != null && !parameters.isEmpty()) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        builder.field(INCLUDE_OUTPUT_IN_AGENT_RESPONSE, includeOutputInAgentResponse);
        if (configMap != null && !configMap.isEmpty()) {
            builder.field(CONFIG_FIELD, configMap);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (runtimeResources != null && !runtimeResources.isEmpty()) {
            builder.field(RUN_TIME_RESOURCES_FIELD, runtimeResources);
        }
        builder.endObject();
        return builder;
    }

    public static MLToolSpec parse(XContentParser parser) throws IOException {
        String type = null;
        String name = null;
        String description = null;
        Map<String, String> attributes = null;
        Map<String, String> parameters = null;
        boolean includeOutputInAgentResponse = false;
        Map<String, String> configMap = null;
        String tenantId = null;
        Map<String, Object> runtimeResources = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TOOL_TYPE_FIELD:
                    type = parser.text();
                    break;
                case TOOL_NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case ATTRIBUTES_FIELD:
                    attributes = getParameterMap(parser.map());
                    break;
                case PARAMETERS_FIELD:
                    parameters = getParameterMap(parser.map());
                    break;
                case INCLUDE_OUTPUT_IN_AGENT_RESPONSE:
                    includeOutputInAgentResponse = parser.booleanValue();
                    break;
                case CONFIG_FIELD:
                    configMap = getParameterMap(parser.map());
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                case RUN_TIME_RESOURCES_FIELD:
                    runtimeResources = parser.map();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLToolSpec
            .builder()
            .type(type)
            .name(name)
            .description(description)
            .attributes(attributes)
            .parameters(parameters)
            .includeOutputInAgentResponse(includeOutputInAgentResponse)
            .configMap(configMap)
            .tenantId(tenantId)
            .runtimeResources(runtimeResources)
            .build();
    }

    public static MLToolSpec fromStream(StreamInput in) throws IOException {
        return new MLToolSpec(in);
    }

    public void addRuntimeResource(String key, Object value) {
        if (this.runtimeResources == null) {
            this.runtimeResources = new HashMap<>();
        }
        this.runtimeResources.put(key, value);
    }

    public Object getRuntimeResource(String key) {
        return this.runtimeResources.get(key);
    }
}

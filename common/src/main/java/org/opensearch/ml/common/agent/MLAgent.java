/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.telemetry.metrics.tags.Tags;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
public class MLAgent implements ToXContentObject, Writeable {
    public static final String AGENT_NAME_FIELD = "name";
    public static final String AGENT_TYPE_FIELD = "type";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String LLM_FIELD = "llm";
    public static final String MODEL_FIELD = "model";
    public static final String TOOLS_FIELD = "tools";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String MEMORY_FIELD = "memory";
    public static final String MEMORY_ID_FIELD = "memory_id";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String APP_TYPE_FIELD = "app_type";
    public static final String IS_HIDDEN_FIELD = "is_hidden";
    public static final String CONTEXT_MANAGEMENT_NAME_FIELD = "context_management_name";
    public static final String CONTEXT_MANAGEMENT_FIELD = "context_management";
    private static final String LLM_INTERFACE_FIELD = "_llm_interface";
    private static final String TAG_VALUE_UNKNOWN = "unknown";
    private static final String TAG_MEMORY_TYPE = "memory_type";

    public static final int AGENT_NAME_MAX_LENGTH = 128;

    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_HIDDEN_AGENT = CommonValue.VERSION_2_13_0;
    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_CONTEXT_MANAGEMENT = CommonValue.VERSION_3_5_0;

    private String name;
    private String type;
    private String description;
    private LLMSpec llm;
    private MLAgentModelSpec model;
    private List<MLToolSpec> tools;
    private Map<String, String> parameters;
    private MLMemorySpec memory;

    private Instant createdTime;
    private Instant lastUpdateTime;
    private String appType;
    private Boolean isHidden;
    private String contextManagementName;
    private ContextManagementTemplate contextManagement;
    private String tenantId;

    @Builder(toBuilder = true)
    public MLAgent(
        String name,
        String type,
        String description,
        LLMSpec llm,
        MLAgentModelSpec model,
        List<MLToolSpec> tools,
        Map<String, String> parameters,
        MLMemorySpec memory,
        Instant createdTime,
        Instant lastUpdateTime,
        String appType,
        Boolean isHidden,
        String contextManagementName,
        ContextManagementTemplate contextManagement,
        String tenantId
    ) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.llm = llm;
        this.model = model;
        this.tools = tools;
        this.parameters = parameters;
        this.memory = memory;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
        this.appType = appType;
        // is_hidden field isn't going to be set by user. It will be set by the code.
        this.isHidden = isHidden;
        this.contextManagementName = contextManagementName;
        this.contextManagement = contextManagement;
        this.tenantId = tenantId;
        validate();
    }

    // Backward compatible constructor for existing tests
    public MLAgent(
        String name,
        String type,
        String description,
        LLMSpec llm,
        List<MLToolSpec> tools,
        Map<String, String> parameters,
        MLMemorySpec memory,
        Instant createdTime,
        Instant lastUpdateTime,
        String appType,
        Boolean isHidden,
        String contextManagementName,
        ContextManagementTemplate contextManagement,
        String tenantId
    ) {
        this(
            name,
            type,
            description,
            llm,
            null,
            tools,
            parameters,
            memory,
            createdTime,
            lastUpdateTime,
            appType,
            isHidden,
            contextManagementName,
            contextManagement,
            tenantId
        );
    }

    private void validate() {
        if (name == null) {
            throw new IllegalArgumentException("Agent name can't be null");
        }
        if (name.isBlank() || name.length() > AGENT_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Agent name cannot be empty or exceed max length of %d characters", MLAgent.AGENT_NAME_MAX_LENGTH)
            );
        }
        validateMLAgentType(type);
        if ((type.equalsIgnoreCase(MLAgentType.CONVERSATIONAL.toString())
            || type.equalsIgnoreCase(MLAgentType.PLAN_EXECUTE_AND_REFLECT.toString())) && llm == null && model == null) {
            throw new IllegalArgumentException("We need model information for the conversational agent type");
        }
        Set<String> toolNames = new HashSet<>();
        if (tools != null) {
            for (MLToolSpec toolSpec : tools) {
                String toolName = Optional.ofNullable(toolSpec.getName()).orElse(toolSpec.getType());
                if (toolNames.contains(toolName)) {
                    throw new IllegalArgumentException("Duplicate tool defined in agent configuration");
                } else {
                    toolNames.add(toolName);
                }
            }
        }
        validateContextManagement();
    }

    private void validateContextManagement() {
        if (contextManagementName != null && contextManagement != null) {
            throw new IllegalArgumentException("Cannot specify both context_management_name and context_management");
        }

        if (contextManagement != null && !contextManagement.isValid()) {
            throw new IllegalArgumentException("Invalid context management configuration");
        }
    }

    private void validateMLAgentType(String agentType) {
        if (type == null) {
            throw new IllegalArgumentException("Agent type can't be null");
        } else {
            try {
                MLAgentType.valueOf(agentType.toUpperCase(Locale.ROOT)); // Use toUpperCase() to allow case-insensitive matching
            } catch (IllegalArgumentException e) {
                // The typeStr does not match any MLAgentType, so throw a new exception with a clearer message.
                throw new IllegalArgumentException("Invalid Agent Type");
            }
        }
    }

    public MLAgent(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        name = input.readString();
        type = input.readString();
        description = input.readOptionalString();
        if (input.readBoolean()) {
            llm = new LLMSpec(input);
        }
        if (input.readBoolean()) {
            model = new MLAgentModelSpec(input);
        }
        if (input.readBoolean()) {
            tools = new ArrayList<>();
            int size = input.readInt();
            for (int i = 0; i < size; i++) {
                tools.add(new MLToolSpec(input));
            }
        }
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        if (input.readBoolean()) {
            memory = new MLMemorySpec(input);
        }
        createdTime = input.readOptionalInstant();
        lastUpdateTime = input.readOptionalInstant();
        appType = input.readOptionalString();
        // is_hidden field isn't going to be set by user. It will be set by the code.
        if (streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_HIDDEN_AGENT)) {
            isHidden = input.readOptionalBoolean();
        }
        if (streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CONTEXT_MANAGEMENT)) {
            contextManagementName = input.readOptionalString();
            if (input.readBoolean()) {
                contextManagement = new ContextManagementTemplate(input);
            }
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
        validate();
    }

    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeString(name);
        out.writeString(type);
        out.writeOptionalString(description);
        if (llm != null) {
            out.writeBoolean(true);
            llm.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
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
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);
        out.writeOptionalString(appType);
        // is_hidden field isn't going to be set by user. It will be set by the code.
        if (streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_HIDDEN_AGENT)) {
            out.writeOptionalBoolean(isHidden);
        }
        if (streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CONTEXT_MANAGEMENT)) {
            out.writeOptionalString(contextManagementName);
            if (contextManagement != null) {
                out.writeBoolean(true);
                contextManagement.writeTo(out);
            } else {
                out.writeBoolean(false);
            }
        }
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
    }

    /**
     * Remove credentials from the agent's model spec.
     * Should be called before returning to user-facing APIs.
     */
    public void removeCredential() {
        if (this.model != null) {
            this.model.removeCredential();
        }
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
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (appType != null) {
            builder.field(APP_TYPE_FIELD, appType);
        }
        // is_hidden field isn't going to be set by user. It will be set by the code.
        if (isHidden != null) {
            builder.field(MLModel.IS_HIDDEN_FIELD, isHidden);
        }
        if (contextManagementName != null) {
            builder.field(CONTEXT_MANAGEMENT_NAME_FIELD, contextManagementName);
        }
        if (contextManagement != null) {
            builder.field(CONTEXT_MANAGEMENT_FIELD, contextManagement);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLAgent parse(XContentParser parser) throws IOException {
        return parseCommonFields(parser, true); // true to parse isHidden field
    }

    public static MLAgent parseFromUserInput(XContentParser parser) throws IOException {
        return parseCommonFields(parser, false); // false to skip isHidden field
    }

    private static MLAgent parseCommonFields(XContentParser parser, boolean parseHidden) throws IOException {
        String name = null;
        String type = null;
        String description = null;
        LLMSpec llm = null;
        MLAgentModelSpec model = null;
        List<MLToolSpec> tools = null;
        Map<String, String> parameters = null;
        MLMemorySpec memory = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        String appType = null;
        boolean isHidden = false;
        String contextManagementName = null;
        ContextManagementTemplate contextManagement = null;
        String tenantId = null;

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
                case MODEL_FIELD:
                    model = MLAgentModelSpec.parse(parser);
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
                case IS_HIDDEN_FIELD:
                    if (parseHidden)
                        isHidden = parser.booleanValue();
                    break;
                case CONTEXT_MANAGEMENT_NAME_FIELD:
                    contextManagementName = parser.text();
                    break;
                case CONTEXT_MANAGEMENT_FIELD:
                    contextManagement = ContextManagementTemplate.parse(parser);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLAgent
            .builder()
            .name(name)
            .type(type)
            .description(description)
            .llm(llm)
            .model(model)
            .tools(tools)
            .parameters(parameters)
            .memory(memory)
            .createdTime(createdTime)
            .lastUpdateTime(lastUpdateTime)
            .appType(appType)
            .isHidden(isHidden)
            .contextManagementName(contextManagementName)
            .contextManagement(contextManagement)
            .tenantId(tenantId)
            .build();
    }

    public static MLAgent fromStream(StreamInput in) throws IOException {
        return new MLAgent(in);
    }

    /**
     * Generates telemetry tags for the ML agent to support metrics collection and monitoring.
     * 
     * @return Tags object containing agent metadata including:
     *         - is_hidden: Whether the agent is hidden (boolean)
     *         - type: Agent type (e.g., conversational, flow)
     *         - memory_type: Memory configuration type if memory is configured
     *         - _llm_interface: LLM interface parameter if specified in parameters
     */
    public Tags getTags() {
        Tags tags = Tags
            .create()
            .addTag(IS_HIDDEN_FIELD, isHidden != null ? isHidden : false)
            .addTag(AGENT_TYPE_FIELD, type != null ? type : TAG_VALUE_UNKNOWN);

        if (memory != null && memory.getType() != null) {
            tags.addTag(TAG_MEMORY_TYPE, memory.getType());
        }

        if (parameters != null && parameters.get(LLM_INTERFACE_FIELD) != null) {
            tags.addTag(LLM_INTERFACE_FIELD, parameters.get(LLM_INTERFACE_FIELD));
        }

        return tags;
    }

    /**
     * Check if this agent has context management configuration
     * @return true if agent has either context management name or inline configuration
     */
    public boolean hasContextManagement() {
        return contextManagementName != null || contextManagement != null;
    }

    /**
     * Get the effective context management configuration for this agent.
     * This method prioritizes inline configuration over template reference.
     * Note: Template resolution requires external service call and should be handled by the caller.
     * 
     * @return the inline context management configuration, or null if using template reference or no configuration
     */
    public ContextManagementTemplate getInlineContextManagement() {
        return contextManagement;
    }

    /**
     * Check if this agent uses a context management template reference
     * @return true if agent references a context management template by name
     */
    public boolean hasContextManagementTemplate() {
        return contextManagementName != null;
    }

    /**
     * Get the context management template name if this agent references one
     * @return the template name, or null if no template reference
     */
    public String getContextManagementTemplateName() {
        return contextManagementName;
    }
}

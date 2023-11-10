/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;


@Getter
public class MLToolSpec implements ToXContentObject {
    public static final String TOOL_NAME_FIELD = "name";
    public static final String ALIAS_FIELD = "alias";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String INCLUDE_OUTPUT_IN_AGENT_RESPONSE = "include_output_in_agent_response";

    private String name;
    private String alias;
    private String description;
    private Map<String, String> parameters;
    private Boolean includeOutputInAgentResponse;


    @Builder(toBuilder = true)
    public MLToolSpec(String name,
                      String alias,
                      String description,
                      Map<String, String> parameters,
                      Boolean includeOutputInAgentResponse) {
        if (name == null) {
            throw new IllegalArgumentException("agent name is null");
        }
        this.name = name;
        this.alias = alias;
        this.description = description;
        this.parameters = parameters;
        this.includeOutputInAgentResponse = includeOutputInAgentResponse;
    }

    public MLToolSpec(StreamInput input) throws IOException{
        name = input.readString();
        alias = input.readOptionalString();
        description = input.readOptionalString();
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        includeOutputInAgentResponse = input.readBoolean();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeOptionalString(alias);
        out.writeOptionalString(description);
        if (parameters != null && parameters.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(includeOutputInAgentResponse);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(TOOL_NAME_FIELD, name);
        }
        if (alias != null) {
            builder.field(ALIAS_FIELD, alias);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (parameters != null && parameters.size() > 0) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (includeOutputInAgentResponse != null) {
            builder.field(INCLUDE_OUTPUT_IN_AGENT_RESPONSE, includeOutputInAgentResponse);
        }
        builder.endObject();
        return builder;
    }

    public static MLToolSpec parse(XContentParser parser) throws IOException {
        String name = null;
        String alias = null;
        String description = null;
        Map<String, String> parameters = null;
        Boolean returnDirect = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TOOL_NAME_FIELD:
                    name = parser.text();
                    break;
                case ALIAS_FIELD:
                    alias = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case PARAMETERS_FIELD:
                    parameters = getParameterMap(parser.map());
                    break;
                case INCLUDE_OUTPUT_IN_AGENT_RESPONSE:
                    returnDirect = parser.booleanValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLToolSpec.builder()
                .name(name)
                .alias(alias)
                .description(description)
                .parameters(parameters)
                .includeOutputInAgentResponse(returnDirect)
                .build();
    }

    public static MLToolSpec fromStream(StreamInput in) throws IOException {
        MLToolSpec toolSpec = new MLToolSpec(in);
        return toolSpec;
    }
}

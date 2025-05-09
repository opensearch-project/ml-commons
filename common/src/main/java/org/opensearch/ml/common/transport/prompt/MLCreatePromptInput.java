/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
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

import lombok.Builder;
import lombok.Data;
import lombok.Setter;

/**
 * MLCreatePromptInput is the input class for MLCreatePromptAction. It contains the parameters needed to create a prompt.
 */
@Data
public class MLCreatePromptInput implements ToXContentObject, Writeable {
    public static final String PROMPT_NAME_FIELD = "name";
    public static final String PROMPT_DESCRIPTION_FIELD = "description";
    public static final String PROMPT_VERSION_FIELD = "version";
    public static final String PROMPT_PROMPT_FIELD = "prompt";
    public static final String PROMPT_TAGS_FIELD = "tags";

    public static final String PROMPT_FIELD_USER_PROMPT = "user";
    public static final String PROMPT_FIELD_SYSTEM_PROMPT = "system";

    private String name;
    private String description;
    private String version;
    private Map<String, String> prompt;
    private List<String> tags;
    @Setter
    private String tenantId;

    /**
     * Constructor to pass values to the MLCreatePromptInput constructor.
     *
     * @param name The name of the prompt passed by user in create request body
     * @param description The description of the prompt passed by user in create request body
     * @param version The version of the prompt passed by user in create request body
     * @param prompt The prompt passed by user in create request body
     * @param tags The tags passed by user in create request body
     * @param tenantId The tenant id
     */
    @Builder(toBuilder = true)
    public MLCreatePromptInput(
        String name,
        String description,
        String version,
        Map<String, String> prompt,
        List<String> tags,
        String tenantId
    ) {
        if (name == null) {
            throw new IllegalArgumentException("MLPrompt name field is null");
        }
        if (prompt == null) {
            throw new IllegalArgumentException("MLPrompt prompt field is null");
        }
        if (prompt.isEmpty()) {
            throw new IllegalArgumentException("MLPrompt prompt field cannot be empty");
        }
        if (!prompt.containsKey(PROMPT_FIELD_SYSTEM_PROMPT)) {
            throw new IllegalArgumentException("MLPrompt prompt field requires " + PROMPT_FIELD_SYSTEM_PROMPT + " parameter");
        }
        if (!prompt.containsKey(PROMPT_FIELD_USER_PROMPT)) {
            throw new IllegalArgumentException("MLPrompt prompt field requires " + PROMPT_FIELD_USER_PROMPT + " parameter");
        }
        if (version == null) {
            throw new IllegalArgumentException("MLPrompt version field is null");
        }

        this.name = name;
        this.description = description;
        this.version = version;
        this.prompt = prompt;
        this.tags = tags;
        this.tenantId = tenantId;
    }

    /**
     * Deserialize the Stream Input and constructs MLCreatePromptInput
     *
     * @param input Abstract class that describes Stream Input
     * @throws IOException thrown if an I/O exception occurred while reading the object from StreamInput
     */
    public MLCreatePromptInput(StreamInput input) throws IOException {
        this.name = input.readOptionalString();
        this.description = input.readOptionalString();
        this.version = input.readOptionalString();
        this.prompt = input.readMap(s -> s.readString(), s -> s.readString());
        this.tags = input.readList(StreamInput::readString);
        this.tenantId = input.readOptionalString();
    }

    /**
     * Write MLCreatePromptInput object to StreamOutput
     *
     * @param output Abstract class that describes Stream Output
     * @throws IOException thrown if an I/O exception occurred while writing the object to StreamOutput
     */
    @Override
    public void writeTo(StreamOutput output) throws IOException {
        output.writeOptionalString(name);
        output.writeOptionalString(description);
        output.writeOptionalString(version);
        output.writeMap(prompt, StreamOutput::writeString, StreamOutput::writeString);
        output.writeCollection(tags, StreamOutput::writeString);
        output.writeOptionalString(tenantId);
    }

    /**
     * Parse XContent field values and Create MLCreatePromptInput object
     *
     * @param parser XContentParser
     * @return MLCreatePromptInput
     * @throws IOException if an I/O exception occurred while parsing the XContent
     */
    public static MLCreatePromptInput parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        String version = null;
        Map<String, String> prompt = null;
        List<String> tags = null;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case PROMPT_NAME_FIELD:
                    name = parser.text();
                    break;
                case PROMPT_DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case PROMPT_VERSION_FIELD:
                    version = parser.text();
                    break;
                case PROMPT_PROMPT_FIELD:
                    prompt = getParameterMap(parser.map());
                    break;
                case PROMPT_TAGS_FIELD:
                    tags = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        tags.add(parser.text());
                    }
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLCreatePromptInput
            .builder()
            .name(name)
            .description(description)
            .version(version)
            .prompt(prompt)
            .tags(tags)
            .tenantId(tenantId)
            .build();
    }

    /**
     * Write MLCreatePromptInput object to XContent
     *
     * @param builder XContentBuilder
     * @param params Parameters
     * @return XContentBuilder
     * @throws IOException thrown if an I/O exception occurred while writing the object to XContent
     */
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(PROMPT_NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(PROMPT_DESCRIPTION_FIELD, description);
        }
        if (version != null) {
            builder.field(PROMPT_VERSION_FIELD, version);
        }
        if (prompt != null) {
            builder.field(PROMPT_PROMPT_FIELD, prompt);
        }
        if (tags != null) {
            builder.field(PROMPT_TAGS_FIELD, tags);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.time.Instant;
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
 * MLUpdatePromptInput is the input class for MLUpdatePromptAction. It contains the parameters
 * that need to be updated
 */
@Data
public class MLUpdatePromptInput implements ToXContentObject, Writeable {
    public static final String PROMPT_NAME_FIELD = "name";
    public static final String PROMPT_DESCRIPTION_FIELD = "description";
    public static final String PROMPT_VERSION_FIELD = "version";
    public static final String PROMPT_PROMPT_FIELD = "prompt";
    public static final String PROMPT_TAGS_FIELD = "tags";

    public static final String PROMPT_FIELD_USER_PROMPT = "user";
    public static final String PROMPT_FIELD_SYSTEM_PROMPT = "system";

    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";

    private String name;
    private String description;
    private String version;
    private Map<String, String> prompt;
    private List<String> tags;
    @Setter
    private String tenantId;
    private Instant lastUpdateTime;

    /**
     * Constructor to pass values to the MLUpdatePromptInput constructor.
     *
     * @param name The name of the prompt passed by user in update request body
     * @param description The description of the prompt passed by user in update request body
     * @param prompt The prompt passed by user in update request body
     * @param tags The tags passed by user in update request body
     * @param tenantId The tenant id
     */
    @Builder(toBuilder = true)
    public MLUpdatePromptInput(
        String name,
        String description,
        String version,
        Map<String, String> prompt,
        List<String> tags,
        String tenantId,
        Instant lastUpdateTime
    ) {
        // TODO: I will include unique name restriction check in my model execution
        this.name = name;
        this.description = description;
        this.version = version;
        this.prompt = prompt;
        this.tags = tags;
        this.tenantId = tenantId;
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * Deserialize the Stream Input and constructs MLUpdatePromptInput
     *
     * @param input Abstract class that describes Stream Input
     * @throws IOException thrown if an I/O exception occurred while reading the object from StreamInput
     */
    public MLUpdatePromptInput(StreamInput input) throws IOException {
        this.name = input.readOptionalString();
        this.description = input.readOptionalString();
        this.version = input.readOptionalString();
        this.prompt = input.readMap(s -> s.readString(), s -> s.readString());
        this.tags = input.readList(StreamInput::readString);
        this.tenantId = input.readOptionalString();
        this.lastUpdateTime = input.readOptionalInstant();
    }

    /**
     * Write MLUpdatePromptInput object to StreamOutput
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
        output.writeOptionalInstant(lastUpdateTime);
    }

    /**
     * Write MLUpdatePromptInput object to XContent
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
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }

    /**
     * Parse XContent field values and Create MLUpdatePromptInput object
     *
     * @param parser XContentParser
     * @return MLUpdatePromptInput
     * @throws IOException if an I/O exception occurred while parsing the XContent
     */
    public static MLUpdatePromptInput parse(XContentParser parser) throws IOException {
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
        return MLUpdatePromptInput
            .builder()
            .name(name)
            .description(description)
            .version(version)
            .prompt(prompt)
            .tags(tags)
            .tenantId(tenantId)
            .build();
    }
}

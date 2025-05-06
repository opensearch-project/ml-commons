/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * MLPrompt is the class to store prompt information.
 */
@Getter
@EqualsAndHashCode
public class MLPrompt implements ToXContentObject, Writeable {

    public static final String PROMPT_ID_FIELD = "prompt_id";
    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String VERSION_FIELD = "version";
    public static final String PROMPT_FIELD = "prompt";
    public static final String TAGS_FIELD = "tags";
    public static final String CREATE_TIME_FIELD = "create_time";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";

    private String promptId;
    private String name;
    private String description;
    private String version;
    private Map<String, String> prompt;
    private List<String> tags;
    private String tenantId;
    private Instant createTime;
    private Instant lastUpdateTime;

    /**
     * Constructor to pass values to the MLPrompt constructor
     *
     * @param promptId The prompt id of the MLPrompt
     * @param name The name of the MLPrompt
     * @param description The description of the MLPrompt
     * @param version The version of the MLPrompt
     * @param prompt The prompt of the MLPrompt -> contains system and user prompts
     * @param tags The tags of the MLPrompt
     * @param tenantId The tenant id of the MLPrompt
     * @param createTime The create time of the MLPrompt
     * @param lastUpdateTime The last update time of the MLPrompt
     */
    @Builder(toBuilder = true)
    public MLPrompt(
        String promptId,
        String name,
        String description,
        String version,
        Map<String, String> prompt,
        List<String> tags,
        String tenantId,
        Instant createTime,
        Instant lastUpdateTime
    ) {
        this.promptId = promptId;
        this.name = name;
        this.description = description;
        this.version = version;
        this.prompt = prompt;
        this.tags = tags;
        this.tenantId = tenantId;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * Deserialize the Stream Input and constructs MLPrompt
     *
     * @param input Abstract class that describes Stream Input
     * @throws IOException if an I/O exception occurred while reading from input stream
     */
    public MLPrompt(StreamInput input) throws IOException {
        this.promptId = input.readOptionalString();
        this.name = input.readOptionalString();
        this.description = input.readOptionalString();
        this.version = input.readOptionalString();
        this.prompt = input.readMap(s -> s.readString(), s -> s.readString());
        this.tags = input.readOptionalStringList();
        this.tenantId = input.readOptionalString();
        this.createTime = input.readInstant();
        this.lastUpdateTime = input.readInstant();
    }

    /**
     * Serialize and Writes the MLPrompt object to the output stream.
     *
     * @param out Abstract class that describes Stream Output
     * @throws IOException if an I/O exception occurred while writing to the output stream
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(promptId);
        out.writeOptionalString(name);
        out.writeOptionalString(description);
        out.writeOptionalString(version);
        out.writeMap(prompt, StreamOutput::writeString, StreamOutput::writeString);
        out.writeCollection(tags, StreamOutput::writeString);
        out.writeOptionalString(tenantId);
        out.writeInstant(createTime);
        out.writeInstant(lastUpdateTime);
    }

    /**
     * Serialize and Writes the MLPrompt object to the XContentBuilder
     *
     * @param xContentBuilder XContentBuilder
     * @param params Parameters that need to be written to xContentBuilder
     * @return XContentBuilder
     * @throws IOException if an I/O exception occurred while writing field values to xContent
     */
    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (promptId != null) {
            builder.field(PROMPT_ID_FIELD, promptId);
        }
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (version != null) {
            builder.field(VERSION_FIELD, version);
        }
        if (prompt != null) {
            builder.field(PROMPT_FIELD, prompt);
        }
        if (tags != null) {
            builder.field(TAGS_FIELD, tags);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (createTime != null) {
            builder.field(CREATE_TIME_FIELD, createTime);
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATE_TIME_FIELD, lastUpdateTime);
        }
        return builder.endObject();
    }

    /**
     * Creates MLPrompt from stream input
     *
     * @param in Input Stream
     * @return MLPrompt
     * @throws IOException if an I/O exception occurred while reading from input stream
     */
    public static MLPrompt fromStream(StreamInput in) throws IOException {
        return new MLPrompt(in);
    }

    /**
     * Creates MLPrompt from XContentParser
     *
     * @param parser XContentParser
     * @return MLPrompt
     * @throws IOException if an I/O exception occurred while parsing the XContentParser into MLPrompt fields
     */
    public static MLPrompt parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        String version = null;
        Map<String, String> prompt = null;
        List<String> tags = null;
        String tenantId = null;
        Instant createTime = null;
        Instant lastUpdateTime = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.text();
                    break;
                case PROMPT_FIELD:
                    prompt = parser.mapStrings();
                    break;
                case TAGS_FIELD:
                    tags = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        tags.add(parser.text());
                    }
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                case CREATE_TIME_FIELD:
                    createTime = Instant.parse(parser.text());
                    break;
                case LAST_UPDATE_TIME_FIELD:
                    lastUpdateTime = Instant.parse(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLPrompt
            .builder()
            .name(name)
            .description(description)
            .version(version)
            .prompt(prompt)
            .tags(tags)
            .tenantId(tenantId)
            .createTime(createTime)
            .lastUpdateTime(lastUpdateTime)
            .build();
    }
}

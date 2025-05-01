/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;

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
    public static final String PROMPT_PROMPT_FIELD = "prompt";
    public static final String PROMPT_TAG_FIELD = "tag";

    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG = CommonValue.VERSION_2_13_0;

    private String name;
    private String description;
    private Map<String, String> prompt;
    private String tag;
    @Setter
    private String tenantId;
    private boolean updatePrompt;

    /**
     * Constructor to pass values to the MLCreatePromptInput constructor.
     *
     * @param name The name of the prompt passed by user in create request body
     * @param description The description of the prompt passed by user in create request body
     * @param prompt The prompt passed by user in create request body
     * @param tag The tag passed by user in create request body
     * @param tenantId The tenant id
     * @param updatePrompt Set to true if the prompt is being updated, false otherwise
     */
    @Builder(toBuilder = true)
    public MLCreatePromptInput(
            String name,
            String description,
            Map<String, String> prompt,
            String tag,
            String tenantId,
            boolean updatePrompt
    ) {
        if (!updatePrompt) {
            if (name == null) {
                throw new IllegalArgumentException("Prompt name field is null");
            }
            if (prompt == null) {
                throw new IllegalArgumentException("Prompt prompt field is null");
            }
        }
        this.name = name;
        this.description = description;
        this.prompt = prompt;
        this.tag = tag;
        this.tenantId = tenantId;
        this.updatePrompt = updatePrompt;
    }

    /**
     * Deserialize the Stream Input and constructs MLCreatePromptInput
     *
     * @param input Abstract class that describes Stream Input
     * @throws IOException thrown if an I/O exception occurred while reading the object from StreamInput
     */
    public MLCreatePromptInput(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        this.name = input.readOptionalString();
        this.description = input.readOptionalString();
        this.prompt = input.readMap(s -> s.readString(), s -> s.readString());
        this.tag = input.readOptionalString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
        this.updatePrompt = input.readBoolean();
    }

    /**
     * Write MLCreatePromptInput object to StreamOutput
     *
     * @param output Abstract class that describes Stream Output
     * @throws IOException thrown if an I/O exception occurred while writing the object to StreamOutput
     */
    @Override
    public void writeTo(StreamOutput output) throws IOException {
        Version streamOutputVersion = output.getVersion();
        output.writeOptionalString(name);
        output.writeOptionalString(description);
        output.writeMap(prompt, StreamOutput::writeString, StreamOutput::writeString);
        output.writeOptionalString(tag);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            output.writeOptionalString(tenantId);
        }
        output.writeBoolean(updatePrompt);
    }

    /**
     * Parse XContent field values and Create MLCreatePromptInput object
     *
     * @param parser XContentParser
     * @return MLCreatePromptInput
     * @throws IOException if an I/O exception occurred while parsing the XContent
     */
    public static MLCreatePromptInput parse(XContentParser parser) throws IOException {
        return parse(parser, false);
    }

    /**
     * Parse XContent field values and Create MLCreatePromptInput object
     *
     * @param parser XContentParser
     * @param updatePrompt flag to indicate if the MLCreatePromptInput is for updating a prompt
     * @return MLCreatePromptInput
     * @throws IOException if an I/O exception occurred while parsing the XContent
     */
    public static MLCreatePromptInput parse(XContentParser parser, boolean updatePrompt) throws IOException {
        String name = null;
        String description = null;
        Map<String, String> prompt = null;
        String tag = null;
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
                case PROMPT_PROMPT_FIELD:
                    prompt = getParameterMap(parser.map());
                    break;
                case PROMPT_TAG_FIELD:
                    tag = parser.text();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLCreatePromptInput(
                name,
                description,
                prompt,
                tag,
                tenantId,
                updatePrompt
        );
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
        if (prompt != null) {
            builder.field(PROMPT_PROMPT_FIELD, prompt);
        }
        if (tag != null) {
            builder.field(PROMPT_TAG_FIELD, tag);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }
}

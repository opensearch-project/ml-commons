/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context Management Template defines which context managers to use and when.
 * This class represents a registered configuration that can be applied to
 * agent execution to enable dynamic context optimization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ContextManagementTemplate implements ToXContentObject, Writeable {

    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String HOOKS_FIELD = "hooks";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_MODIFIED_FIELD = "last_modified";
    public static final String CREATED_BY_FIELD = "created_by";

    /**
     * Unique name for the context management template
     */
    private String name;

    /**
     * Human-readable description of what this template does
     */
    private String description;

    /**
     * Map of hook names to lists of context manager configurations
     */
    private Map<String, List<ContextManagerConfig>> hooks;

    /**
     * When this template was created
     */
    private Instant createdTime;

    /**
     * When this template was last modified
     */
    private Instant lastModified;

    /**
     * Who created this template
     */
    private String createdBy;

    /**
     * Constructor from StreamInput
     */
    public ContextManagementTemplate(StreamInput input) throws IOException {
        this.name = input.readString();
        this.description = input.readOptionalString();

        // Read hooks map
        int hooksSize = input.readInt();
        if (hooksSize >= 0) {
            this.hooks = input.readMap(StreamInput::readString, in -> {
                try {
                    int listSize = in.readInt();
                    List<ContextManagerConfig> configs = new java.util.ArrayList<>();
                    for (int i = 0; i < listSize; i++) {
                        configs.add(new ContextManagerConfig(in));
                    }
                    return configs;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            this.hooks = null;
        }

        this.createdTime = input.readOptionalInstant();
        this.lastModified = input.readOptionalInstant();
        this.createdBy = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeOptionalString(description);

        // Write hooks map
        if (hooks != null) {
            out.writeInt(hooks.size());
            out.writeMap(hooks, StreamOutput::writeString, (output, configs) -> {
                try {
                    output.writeInt(configs.size());
                    for (ContextManagerConfig config : configs) {
                        config.writeTo(output);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            out.writeInt(0);
        }

        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastModified);
        out.writeOptionalString(createdBy);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (hooks != null && !hooks.isEmpty()) {
            builder.field(HOOKS_FIELD, hooks);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastModified != null) {
            builder.field(LAST_MODIFIED_FIELD, lastModified.toEpochMilli());
        }
        if (createdBy != null) {
            builder.field(CREATED_BY_FIELD, createdBy);
        }

        builder.endObject();
        return builder;
    }

    /**
     * Parse ContextManagementTemplate from XContentParser
     */
    public static ContextManagementTemplate parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        Map<String, List<ContextManagerConfig>> hooks = null;
        Instant createdTime = null;
        Instant lastModified = null;
        String createdBy = null;

        if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
            parser.nextToken();
        }

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
                case HOOKS_FIELD:
                    hooks = parseHooks(parser);
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_MODIFIED_FIELD:
                    lastModified = Instant.ofEpochMilli(parser.longValue());
                    break;
                case CREATED_BY_FIELD:
                    createdBy = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return ContextManagementTemplate
            .builder()
            .name(name)
            .description(description)
            .hooks(hooks)
            .createdTime(createdTime)
            .lastModified(lastModified)
            .createdBy(createdBy)
            .build();
    }

    /**
     * Parse hooks configuration from XContentParser
     */
    private static Map<String, List<ContextManagerConfig>> parseHooks(XContentParser parser) throws IOException {
        Map<String, List<ContextManagerConfig>> hooks = new java.util.HashMap<>();

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String hookName = parser.currentName();
            parser.nextToken(); // Move to START_ARRAY

            List<ContextManagerConfig> configs = new java.util.ArrayList<>();
            while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                configs.add(ContextManagerConfig.parse(parser));
            }

            hooks.put(hookName, configs);
        }

        return hooks;
    }

    /**
     * Validate the template configuration
     */
    public boolean isValid() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // Name must not contain spaces
        if (name.contains(" ")) {
            return false;
        }

        // Name must not contain capital letters
        if (!name.equals(name.toLowerCase())) {
            return false;
        }

        // Name length must be less than 50 characters
        if (name.length() >= 50) {
            return false;
        }

        // Allow null hooks (no context management) but not empty hooks map (misconfiguration)
        if (hooks != null) {
            if (hooks.isEmpty()) {
                return false;
            }

            // Validate all context manager configs
            for (List<ContextManagerConfig> configs : hooks.values()) {
                if (configs != null) {
                    for (ContextManagerConfig config : configs) {
                        if (!config.isValid()) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }
}

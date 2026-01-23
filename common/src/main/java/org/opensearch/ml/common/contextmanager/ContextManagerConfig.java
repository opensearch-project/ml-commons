/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for a context manager within a context management template.
 * This class holds the configuration details for how a specific context manager
 * should be configured and when it should activate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextManagerConfig implements ToXContentObject, Writeable {

    public static final String TYPE_FIELD = "type";
    public static final String ACTIVATION_FIELD = "activation";
    public static final String CONFIG_FIELD = "config";

    /**
     * The type of context manager (e.g., "ToolsOutputTruncateManager")
     */
    private String type;

    /**
     * Activation conditions that determine when this manager should execute
     */
    private Map<String, Object> activation;

    /**
     * Configuration parameters specific to this manager type
     */
    private Map<String, Object> config;

    /**
     * Constructor from StreamInput
     */
    public ContextManagerConfig(StreamInput input) throws IOException {
        this.type = input.readOptionalString();
        this.activation = input.readMap();
        this.config = input.readMap();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(type);
        out.writeMap(activation);
        out.writeMap(config);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        if (type != null) {
            builder.field(TYPE_FIELD, type);
        }
        if (activation != null && !activation.isEmpty()) {
            builder.field(ACTIVATION_FIELD, activation);
        }
        if (config != null && !config.isEmpty()) {
            builder.field(CONFIG_FIELD, config);
        }

        builder.endObject();
        return builder;
    }

    /**
     * Parse ContextManagerConfig from XContentParser
     */
    public static ContextManagerConfig parse(XContentParser parser) throws IOException {
        String type = null;
        Map<String, Object> activation = null;
        Map<String, Object> config = null;

        if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
            parser.nextToken();
        }

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case ACTIVATION_FIELD:
                    activation = parser.map();
                    break;
                case CONFIG_FIELD:
                    config = parser.map();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new ContextManagerConfig(type, activation, config);
    }

    /**
     * Validate the configuration
     * @return true if configuration is valid, false otherwise
     */
    public boolean isValid() {
        return type != null && !type.trim().isEmpty();
    }
}

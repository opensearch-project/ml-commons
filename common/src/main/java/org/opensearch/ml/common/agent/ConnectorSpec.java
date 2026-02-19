/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Specification for agent proxy connector configuration.
 * Represents a single proxy endpoint with optional routing rules.
 */
@Data
@EqualsAndHashCode
public class ConnectorSpec implements ToXContentObject {
    public static final String PROXY_URL_FIELD = "proxy_url";
    public static final String ROUTING_RULES_FIELD = "routing_rules";
    public static final String AUTH_TYPE_FIELD = "auth_type";
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String CONNECTION_TIMEOUT_FIELD = "connection_timeout";
    public static final String READ_TIMEOUT_FIELD = "read_timeout";

    // Default timeouts
    public static final int DEFAULT_CONNECTION_TIMEOUT = 10; // seconds
    public static final int DEFAULT_READ_TIMEOUT = 300; // seconds

    private final String proxyUrl;
    private final List<RoutingRule> routingRules;
    private final String authType;
    private Map<String, String> credential;
    private final Integer connectionTimeout;
    private final Integer readTimeout;

    @Builder(toBuilder = true)
    public ConnectorSpec(
        String proxyUrl,
        List<RoutingRule> routingRules,
        String authType,
        Map<String, String> credential,
        Integer connectionTimeout,
        Integer readTimeout
    ) {
        if (proxyUrl == null || proxyUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("proxy_url is required for connector");
        }

        this.proxyUrl = proxyUrl;
        this.routingRules = routingRules;
        this.authType = authType;
        this.credential = credential;
        this.connectionTimeout = connectionTimeout != null ? connectionTimeout : DEFAULT_CONNECTION_TIMEOUT;
        this.readTimeout = readTimeout != null ? readTimeout : DEFAULT_READ_TIMEOUT;

        // Validate routing rules if present
        if (routingRules != null && !routingRules.isEmpty()) {
            for (RoutingRule rule : routingRules) {
                rule.validate();
            }
        }
    }

    public ConnectorSpec(StreamInput input) throws IOException {
        proxyUrl = input.readString();

        // Read routing rules
        if (input.readBoolean()) {
            int rulesCount = input.readInt();
            routingRules = new ArrayList<>(rulesCount);
            for (int i = 0; i < rulesCount; i++) {
                routingRules.add(new RoutingRule(input));
            }
        } else {
            routingRules = null;
        }

        authType = input.readOptionalString();

        // Read credential
        if (input.readBoolean()) {
            credential = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        } else {
            credential = null;
        }

        connectionTimeout = input.readOptionalInt();
        readTimeout = input.readOptionalInt();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(proxyUrl);

        // Write routing rules
        if (routingRules != null && !routingRules.isEmpty()) {
            out.writeBoolean(true);
            out.writeInt(routingRules.size());
            for (RoutingRule rule : routingRules) {
                rule.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }

        out.writeOptionalString(authType);

        // Write credential
        if (credential != null && !credential.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(credential, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }

        out.writeOptionalInt(connectionTimeout);
        out.writeOptionalInt(readTimeout);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(PROXY_URL_FIELD, proxyUrl);

        if (routingRules != null && !routingRules.isEmpty()) {
            builder.startArray(ROUTING_RULES_FIELD);
            for (RoutingRule rule : routingRules) {
                rule.toXContent(builder, params);
            }
            builder.endArray();
        }

        if (authType != null) {
            builder.field(AUTH_TYPE_FIELD, authType);
        }

        if (credential != null && !credential.isEmpty()) {
            builder.field(CREDENTIAL_FIELD, credential);
        }

        if (connectionTimeout != null) {
            builder.field(CONNECTION_TIMEOUT_FIELD, connectionTimeout);
        }

        if (readTimeout != null) {
            builder.field(READ_TIMEOUT_FIELD, readTimeout);
        }

        builder.endObject();
        return builder;
    }

    /**
     * Remove credentials from this connector spec.
     * Should be called before returning to user-facing APIs.
     */
    public void removeCredential() {
        this.credential = null;
    }

    /**
     * Check if this connector has routing rules (not a catch-all).
     */
    public boolean hasRoutingRules() {
        return routingRules != null && !routingRules.isEmpty();
    }

    public static ConnectorSpec parse(XContentParser parser) throws IOException {
        String proxyUrl = null;
        List<RoutingRule> routingRules = null;
        String authType = null;
        Map<String, String> credential = null;
        Integer connectionTimeout = null;
        Integer readTimeout = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case PROXY_URL_FIELD:
                    proxyUrl = parser.text();
                    break;
                case ROUTING_RULES_FIELD:
                    routingRules = parseRoutingRules(parser);
                    break;
                case AUTH_TYPE_FIELD:
                    authType = parser.text();
                    break;
                case CREDENTIAL_FIELD:
                    credential = getParameterMap(parser.map());
                    break;
                case CONNECTION_TIMEOUT_FIELD:
                    connectionTimeout = parser.intValue();
                    break;
                case READ_TIMEOUT_FIELD:
                    readTimeout = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new ConnectorSpec(proxyUrl, routingRules, authType, credential, connectionTimeout, readTimeout);
    }

    private static List<RoutingRule> parseRoutingRules(XContentParser parser) throws IOException {
        List<RoutingRule> rules = new ArrayList<>();
        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);

        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            rules.add(RoutingRule.parse(parser));
        }

        return rules;
    }

    public static ConnectorSpec fromStream(StreamInput in) throws IOException {
        return new ConnectorSpec(in);
    }

    /**
     * Represents a single routing rule for context-based routing.
     */
    @Data
    @EqualsAndHashCode
    public static class RoutingRule implements ToXContentObject {
        public static final String CONTEXT_DESCRIPTION_FIELD = "context_description";
        public static final String VALUE_PATTERN_FIELD = "value_pattern";

        private final String contextDescription;
        private final String valuePattern;
        private transient Pattern compiledPattern;

        @Builder
        public RoutingRule(String contextDescription, String valuePattern) {
            this.contextDescription = contextDescription;
            this.valuePattern = valuePattern;
            validate();
            compilePattern();
        }

        public RoutingRule(StreamInput input) throws IOException {
            contextDescription = input.readString();
            valuePattern = input.readString();
            compilePattern();
        }

        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(contextDescription);
            out.writeString(valuePattern);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field(CONTEXT_DESCRIPTION_FIELD, contextDescription);
            builder.field(VALUE_PATTERN_FIELD, valuePattern);
            builder.endObject();
            return builder;
        }

        public void validate() {
            if (contextDescription == null || contextDescription.trim().isEmpty()) {
                throw new IllegalArgumentException("context_description is required in routing rule");
            }
            if (valuePattern == null || valuePattern.trim().isEmpty()) {
                throw new IllegalArgumentException("value_pattern is required in routing rule");
            }

            // Validate regex pattern
            try {
                Pattern.compile(valuePattern);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex pattern in value_pattern: " + valuePattern, e);
            }
        }

        private void compilePattern() {
            try {
                this.compiledPattern = Pattern.compile(valuePattern);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex pattern: " + valuePattern, e);
            }
        }

        /**
         * Check if the given value matches this rule's pattern.
         */
        public boolean matches(String value) {
            if (compiledPattern == null) {
                compilePattern();
            }
            return compiledPattern.matcher(value).find();
        }

        public static RoutingRule parse(XContentParser parser) throws IOException {
            String contextDescription = null;
            String valuePattern = null;

            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();

                switch (fieldName) {
                    case CONTEXT_DESCRIPTION_FIELD:
                        contextDescription = parser.text();
                        break;
                    case VALUE_PATTERN_FIELD:
                        valuePattern = parser.text();
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }

            return new RoutingRule(contextDescription, valuePattern);
        }
    }
}

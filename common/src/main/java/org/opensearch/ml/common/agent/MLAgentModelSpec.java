/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.RetryBackoffPolicy;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Specification for model configuration in agent registration
 */
@EqualsAndHashCode
@Getter
@Setter
public class MLAgentModelSpec implements ToXContentObject {
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String MODEL_PROVIDER_FIELD = "model_provider";
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String MODEL_PARAMETERS_FIELD = "model_parameters";
    public static final String CLIENT_CONFIG_FIELD = "client_config";

    /**
     * client_config fields an agent may set: timeouts and retries. TLS settings are excluded; they
     * belong on a standalone connector.
     */
    public static final Set<String> SUPPORTED_CLIENT_CONFIG_FIELDS = Set
        .of(
            ConnectorClientConfig.CONNECTION_TIMEOUT_FIELD,
            ConnectorClientConfig.READ_TIMEOUT_FIELD,
            ConnectorClientConfig.MAX_CONNECTION_FIELD,
            ConnectorClientConfig.MAX_RETRY_TIMES_FIELD,
            ConnectorClientConfig.RETRY_BACKOFF_MILLIS_FIELD,
            ConnectorClientConfig.RETRY_TIMEOUT_SECONDS_FIELD,
            ConnectorClientConfig.RETRY_BACKOFF_POLICY_FIELD
        );
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG = CommonValue.VERSION_3_10_0;

    private final String modelId;
    private final String modelProvider;
    private Map<String, String> credential;
    private Map<String, String> modelParameters;
    /**
     * Overrides for the auto-created connector's client config. Only the fields the user set are
     * non-null; the rest keep the provider's defaults (see AgentModelService).
     */
    private ConnectorClientConfig clientConfig;

    public MLAgentModelSpec(String modelId, String modelProvider, Map<String, String> credential, Map<String, String> modelParameters) {
        this(modelId, modelProvider, credential, modelParameters, null);
    }

    @Builder(toBuilder = true)
    public MLAgentModelSpec(
        String modelId,
        String modelProvider,
        Map<String, String> credential,
        Map<String, String> modelParameters,
        ConnectorClientConfig clientConfig
    ) {
        if (modelId == null) {
            throw new IllegalArgumentException("model_id must be provided");
        }

        if (modelProvider == null) {
            throw new IllegalArgumentException("model_provider must be provided");
        }
        this.modelId = modelId;
        this.modelProvider = modelProvider;
        this.credential = credential;
        this.modelParameters = modelParameters;
        this.clientConfig = clientConfig;
    }

    public MLAgentModelSpec(StreamInput input) throws IOException {
        modelId = input.readString();
        modelProvider = input.readString();
        if (input.readBoolean()) {
            credential = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        if (input.readBoolean()) {
            modelParameters = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        if (input.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG) && input.readBoolean()) {
            clientConfig = new ConnectorClientConfig(input);
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeString(modelProvider);
        if (credential != null && !credential.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(credential, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        if (modelParameters != null && !modelParameters.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(modelParameters, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        if (out.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG)) {
            out.writeBoolean(clientConfig != null);
            if (clientConfig != null) {
                clientConfig.writeTo(out);
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (modelProvider != null) {
            builder.field(MODEL_PROVIDER_FIELD, modelProvider);
        }
        if (credential != null && !credential.isEmpty()) {
            builder.field(CREDENTIAL_FIELD, credential);
        }
        if (modelParameters != null && !modelParameters.isEmpty()) {
            builder.field(MODEL_PARAMETERS_FIELD, modelParameters);
        }
        if (clientConfig != null) {
            builder.field(CLIENT_CONFIG_FIELD, clientConfig);
        }
        builder.endObject();
        return builder;
    }

    /**
     * Remove credentials from this model spec.
     * Should be called before returning to user-facing APIs.
     */
    public void removeCredential() {
        this.credential = null;
    }

    public static MLAgentModelSpec parse(XContentParser parser) throws IOException {
        String model = null;
        String modelProvider = null;
        Map<String, String> credential = null;
        Map<String, String> modelParameters = null;
        ConnectorClientConfig clientConfig = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_ID_FIELD:
                    model = parser.text();
                    break;
                case MODEL_PROVIDER_FIELD:
                    modelProvider = parser.text();
                    break;
                case CREDENTIAL_FIELD:
                    credential = getParameterMap(parser.map());
                    break;
                case MODEL_PARAMETERS_FIELD:
                    modelParameters = getParameterMap(parser.map());
                    break;
                case CLIENT_CONFIG_FIELD:
                    clientConfig = parser.currentToken() == XContentParser.Token.VALUE_NULL ? null : parseClientConfig(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new MLAgentModelSpec(model, modelProvider, credential, modelParameters, clientConfig);
    }

    /**
     * Parses only the fields the user set. Not ConnectorClientConfig.parse: it fills unset fields
     * with defaults, which would reset the agents' 3 retries to 0.
     */
    static ConnectorClientConfig parseClientConfig(XContentParser parser) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        ConnectorClientConfig.ConnectorClientConfigBuilder config = ConnectorClientConfig.builder();
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if (SUPPORTED_CLIENT_CONFIG_FIELDS.contains(fieldName) && parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                throw new IllegalArgumentException("client_config." + fieldName + " must not be null");
            }
            switch (fieldName) {
                case ConnectorClientConfig.CONNECTION_TIMEOUT_FIELD:
                    config.connectionTimeout(positive(fieldName, intValue(parser, fieldName)));
                    break;
                case ConnectorClientConfig.READ_TIMEOUT_FIELD:
                    config.readTimeout(positive(fieldName, intValue(parser, fieldName)));
                    break;
                case ConnectorClientConfig.MAX_CONNECTION_FIELD:
                    config.maxConnections(positive(fieldName, intValue(parser, fieldName)));
                    break;
                case ConnectorClientConfig.MAX_RETRY_TIMES_FIELD:
                    int retries = intValue(parser, fieldName);
                    // -1 means "retry until retry_timeout_seconds", as on standalone connectors.
                    if (retries < -1) {
                        throw new IllegalArgumentException("client_config.max_retry_times must be -1 or greater");
                    }
                    config.maxRetryTimes(retries);
                    break;
                case ConnectorClientConfig.RETRY_BACKOFF_MILLIS_FIELD:
                    config.retryBackoffMillis(positive(fieldName, intValue(parser, fieldName)));
                    break;
                case ConnectorClientConfig.RETRY_TIMEOUT_SECONDS_FIELD:
                    config.retryTimeoutSeconds(positive(fieldName, intValue(parser, fieldName)));
                    break;
                case ConnectorClientConfig.RETRY_BACKOFF_POLICY_FIELD:
                    config.retryBackoffPolicy(RetryBackoffPolicy.from(parser.text()));
                    break;
                default:
                    throw new IllegalArgumentException(
                        "client_config."
                            + fieldName
                            + " is not supported for an agent model. Supported fields: "
                            + new TreeSet<>(SUPPORTED_CLIENT_CONFIG_FIELDS)
                            + ". Use a standalone connector for other settings."
                    );
            }
        }
        return config.build();
    }

    /** Reads a whole number, rejecting strings and fractions that intValue() would otherwise coerce (1.9 to 1). */
    private static int intValue(XContentParser parser, String field) throws IOException {
        if (parser.currentToken() != XContentParser.Token.VALUE_NUMBER || parser.numberType() != XContentParser.NumberType.INT) {
            throw new IllegalArgumentException("client_config." + field + " must be an integer");
        }
        return parser.intValue();
    }

    private static int positive(String field, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("client_config." + field + " must be greater than 0");
        }
        return value;
    }

    public static MLAgentModelSpec fromStream(StreamInput in) throws IOException {
        return new MLAgentModelSpec(in);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ConnectorClientConfig implements ToXContentObject, Writeable {

    public static final String MAX_CONNECTION_FIELD = "max_connection";
    public static final String CONNECTION_TIMEOUT_FIELD = "connection_timeout";
    public static final String READ_TIMEOUT_FIELD = "read_timeout";
    public static final String RETRY_BACKOFF_MILLIS_FIELD = "retry_backoff_millis";
    public static final String RETRY_TIMEOUT_SECONDS_FIELD = "retry_timeout_seconds";
    public static final String MAX_RETRY_TIMES_FIELD = "max_retry_times";
    public static final String RETRY_BACKOFF_POLICY_FIELD = "retry_backoff_policy";

    public static final int MAX_CONNECTION_DEFAULT_VALUE = 30;
    public static final int CONNECTION_TIMEOUT_DEFAULT_VALUE = 1000;
    public static final int READ_TIMEOUT_DEFAULT_VALUE = 10000;
    public static final int RETRY_BACKOFF_MILLIS_DEFAULT_VALUE = 200;
    public static final int RETRY_TIMEOUT_SECONDS_DEFAULT_VALUE = 30;
    public static final int MAX_RETRY_TIMES_DEFAULT_VALUE = 0;
    public static final RetryBackoffPolicy RETRY_BACKOFF_POLICY_DEFAULT_VALUE = RetryBackoffPolicy.CONSTANT;
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_RETRY = Version.V_2_15_0;
    private Integer maxConnections;
    private Integer connectionTimeoutMillis;
    private Integer readTimeoutMillis;
    private Integer retryBackoffMillis;
    private Integer retryTimeoutSeconds;
    private Integer maxRetryTimes;
    private RetryBackoffPolicy retryBackoffPolicy;

    @Builder(toBuilder = true)
    public ConnectorClientConfig(
        Integer maxConnections,
        Integer connectionTimeoutMillis,
        Integer readTimeoutMillis,
        Integer retryBackoffMillis,
        Integer retryTimeoutSeconds,
        Integer maxRetryTimes,
        RetryBackoffPolicy retryBackoffPolicy
    ) {
        this.maxConnections = maxConnections;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.retryBackoffMillis = retryBackoffMillis;
        this.retryTimeoutSeconds = retryTimeoutSeconds;
        this.maxRetryTimes = maxRetryTimes;
        this.retryBackoffPolicy = retryBackoffPolicy;
    }

    public ConnectorClientConfig(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        this.maxConnections = input.readOptionalInt();
        this.connectionTimeoutMillis = input.readOptionalInt();
        this.readTimeoutMillis = input.readOptionalInt();
        if (streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_RETRY)) {
            this.retryBackoffMillis = input.readOptionalInt();
            this.retryTimeoutSeconds = input.readOptionalInt();
            this.maxRetryTimes = input.readOptionalInt();
            if (input.readBoolean()) {
                this.retryBackoffPolicy = RetryBackoffPolicy.from(input.readString());
            }
        }
    }

    public ConnectorClientConfig() {
        this.maxConnections = MAX_CONNECTION_DEFAULT_VALUE;
        this.connectionTimeoutMillis = CONNECTION_TIMEOUT_DEFAULT_VALUE;
        this.readTimeoutMillis = READ_TIMEOUT_DEFAULT_VALUE;
        this.retryBackoffMillis = RETRY_BACKOFF_MILLIS_DEFAULT_VALUE;
        this.retryTimeoutSeconds = RETRY_TIMEOUT_SECONDS_DEFAULT_VALUE;
        this.maxRetryTimes = MAX_RETRY_TIMES_DEFAULT_VALUE;
        this.retryBackoffPolicy = RETRY_BACKOFF_POLICY_DEFAULT_VALUE;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalInt(maxConnections);
        out.writeOptionalInt(connectionTimeoutMillis);
        out.writeOptionalInt(readTimeoutMillis);
        if (streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_RETRY)) {
            out.writeOptionalInt(retryBackoffMillis);
            out.writeOptionalInt(retryTimeoutSeconds);
            out.writeOptionalInt(maxRetryTimes);
            if (Objects.nonNull(retryBackoffPolicy)) {
                out.writeBoolean(true);
                out.writeString(retryBackoffPolicy.name());
            } else {
                out.writeBoolean(false);
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (maxConnections != null) {
            builder.field(MAX_CONNECTION_FIELD, maxConnections);
        }
        if (connectionTimeoutMillis != null) {
            builder.field(CONNECTION_TIMEOUT_FIELD, connectionTimeoutMillis);
        }
        if (readTimeoutMillis != null) {
            builder.field(READ_TIMEOUT_FIELD, readTimeoutMillis);
        }
        if (retryBackoffMillis != null) {
            builder.field(RETRY_BACKOFF_MILLIS_FIELD, retryBackoffMillis);
        }
        if (retryTimeoutSeconds != null) {
            builder.field(RETRY_TIMEOUT_SECONDS_FIELD, retryTimeoutSeconds);
        }
        if (maxRetryTimes != null) {
            builder.field(MAX_RETRY_TIMES_FIELD, maxRetryTimes);
        }
        if (retryBackoffPolicy != null) {
            builder.field(RETRY_BACKOFF_POLICY_FIELD, retryBackoffPolicy.name().toLowerCase(Locale.ROOT));
        }
        return builder.endObject();
    }

    public static ConnectorClientConfig fromStream(StreamInput in) throws IOException {
        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig(in);
        return connectorClientConfig;
    }

    public static ConnectorClientConfig parse(XContentParser parser) throws IOException {
        int maxConnections = MAX_CONNECTION_DEFAULT_VALUE;
        int connectionTimeout = CONNECTION_TIMEOUT_DEFAULT_VALUE;
        int readTimeout = READ_TIMEOUT_DEFAULT_VALUE;
        int retryBackoffMillis = RETRY_BACKOFF_MILLIS_DEFAULT_VALUE;
        int retryTimeoutSeconds = RETRY_TIMEOUT_SECONDS_DEFAULT_VALUE;
        int maxRetryTimes = MAX_RETRY_TIMES_DEFAULT_VALUE;
        RetryBackoffPolicy retryBackoffPolicy = RETRY_BACKOFF_POLICY_DEFAULT_VALUE;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MAX_CONNECTION_FIELD:
                    maxConnections = parser.intValue();
                    break;
                case CONNECTION_TIMEOUT_FIELD:
                    connectionTimeout = parser.intValue();
                    break;
                case READ_TIMEOUT_FIELD:
                    readTimeout = parser.intValue();
                    break;
                case RETRY_BACKOFF_MILLIS_FIELD:
                    retryBackoffMillis = parser.intValue();
                    break;
                case RETRY_TIMEOUT_SECONDS_FIELD:
                    retryTimeoutSeconds = parser.intValue();
                    break;
                case MAX_RETRY_TIMES_FIELD:
                    maxRetryTimes = parser.intValue();
                    break;
                case RETRY_BACKOFF_POLICY_FIELD:
                    retryBackoffPolicy = RetryBackoffPolicy.from(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return ConnectorClientConfig
            .builder()
            .maxConnections(maxConnections)
            .connectionTimeoutMillis(connectionTimeout)
            .readTimeoutMillis(readTimeout)
            .retryBackoffMillis(retryBackoffMillis)
            .retryTimeoutSeconds(retryTimeoutSeconds)
            .maxRetryTimes(maxRetryTimes)
            .retryBackoffPolicy(retryBackoffPolicy)
            .build();
    }
}

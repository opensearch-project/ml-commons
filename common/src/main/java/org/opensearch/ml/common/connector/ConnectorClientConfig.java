/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

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

    public static final Integer MAX_CONNECTION_DEFAULT_VALUE = Integer.valueOf(30);
    public static final Integer CONNECTION_TIMEOUT_DEFAULT_VALUE = Integer.valueOf(30000);
    public static final Integer READ_TIMEOUT_DEFAULT_VALUE = Integer.valueOf(30000);
    public static final Integer RETRY_BACKOFF_MILLIS_DEFAULT_VALUE = 200;
    public static final Integer RETRY_TIMEOUT_SECONDS_DEFAULT_VALUE = 30;
    public static final Integer MAX_RETRY_TIMES_DEFAULT_VALUE = 0;
    public static final RetryBackoffPolicy RETRY_BACKOFF_POLICY_DEFAULT_VALUE = RetryBackoffPolicy.CONSTANT;
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_RETRY = Version.V_2_15_0;
    private Integer maxConnections;
    private Integer connectionTimeout;
    private Integer readTimeout;
    private Integer retryBackoffMillis;
    private Integer retryTimeoutSeconds;
    private Integer maxRetryTimes;
    private RetryBackoffPolicy retryBackoffPolicy;

    @Builder(toBuilder = true)
    public ConnectorClientConfig(
        Integer maxConnections,
        Integer connectionTimeout,
        Integer readTimeout,
        Integer retryBackoffMillis,
        Integer retryTimeoutSeconds,
        Integer maxRetryTimes,
        RetryBackoffPolicy retryBackoffPolicy
    ) {
        this.maxConnections = maxConnections;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.retryBackoffMillis = retryBackoffMillis;
        this.retryTimeoutSeconds = retryTimeoutSeconds;
        this.maxRetryTimes = maxRetryTimes;
        this.retryBackoffPolicy = retryBackoffPolicy;
    }

    public ConnectorClientConfig(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        this.maxConnections = input.readOptionalInt();
        this.connectionTimeout = input.readOptionalInt();
        this.readTimeout = input.readOptionalInt();
        if(streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_RETRY)) {
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
        this.connectionTimeout = CONNECTION_TIMEOUT_DEFAULT_VALUE;
        this.readTimeout = READ_TIMEOUT_DEFAULT_VALUE;
        this.retryBackoffMillis = RETRY_BACKOFF_MILLIS_DEFAULT_VALUE;
        this.retryTimeoutSeconds = RETRY_TIMEOUT_SECONDS_DEFAULT_VALUE;
        this.maxRetryTimes = MAX_RETRY_TIMES_DEFAULT_VALUE;
        this.retryBackoffPolicy = RETRY_BACKOFF_POLICY_DEFAULT_VALUE;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalInt(maxConnections);
        out.writeOptionalInt(connectionTimeout);
        out.writeOptionalInt(readTimeout);
        if(streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_RETRY)){
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
        if (connectionTimeout != null) {
            builder.field(CONNECTION_TIMEOUT_FIELD, connectionTimeout);
        }
        if (readTimeout != null) {
            builder.field(READ_TIMEOUT_FIELD, readTimeout);
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
        Integer maxConnections = MAX_CONNECTION_DEFAULT_VALUE;
        Integer connectionTimeout = CONNECTION_TIMEOUT_DEFAULT_VALUE;
        Integer readTimeout = READ_TIMEOUT_DEFAULT_VALUE;
        Integer retryBackoffMillis = RETRY_BACKOFF_MILLIS_DEFAULT_VALUE;
        Integer retryTimeoutSeconds = RETRY_TIMEOUT_SECONDS_DEFAULT_VALUE;
        Integer maxRetryTimes = MAX_RETRY_TIMES_DEFAULT_VALUE;
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
        return ConnectorClientConfig.builder()
                .maxConnections(maxConnections)
                .connectionTimeout(connectionTimeout)
                .readTimeout(readTimeout)
                .retryBackoffMillis(retryBackoffMillis)
                .retryTimeoutSeconds(retryTimeoutSeconds)
                .maxRetryTimes(maxRetryTimes)
                .retryBackoffPolicy(retryBackoffPolicy)
                .build();
    }
}

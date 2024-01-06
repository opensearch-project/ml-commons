/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
@EqualsAndHashCode
public class ConnectorHttpClientConfig implements ToXContentObject, Writeable {

    public static final String MAX_CONNECTION_FIELD = "max_connection";
    public static final String CONNECTION_TIMEOUT_FIELD = "connection_timeout";
    public static final String READ_TIMEOUT_FIELD = "read_timeout";

    public static final Integer MAX_CONNECTION_DEFAULT_VALUE = Integer.valueOf(30);
    public static final Integer CONNECTION_TIMEOUT_DEFAULT_VALUE = Integer.valueOf(20000);
    public static final Integer READ_TIMEOUT_DEFAULT_VALUE = Integer.valueOf(20000);

    private Integer maxConnections;
    private Integer connectionTimeout;
    private Integer readTimeout;

    @Builder(toBuilder = true)
    public ConnectorHttpClientConfig(
        Integer maxConnections,
        Integer connectionTimeout,
        Integer readTimeout
    ) {
        this.maxConnections = Objects.requireNonNullElse(maxConnections, MAX_CONNECTION_DEFAULT_VALUE);
        this.connectionTimeout = Objects.requireNonNullElse(connectionTimeout, CONNECTION_TIMEOUT_DEFAULT_VALUE);
        this.readTimeout = Objects.requireNonNullElse(readTimeout, READ_TIMEOUT_DEFAULT_VALUE);

    }

    public ConnectorHttpClientConfig(StreamInput input) throws IOException {
        this.maxConnections = input.readOptionalInt();
        this.connectionTimeout = input.readOptionalInt();
        this.readTimeout = input.readOptionalInt();
    }

    public ConnectorHttpClientConfig() {
        this.maxConnections = MAX_CONNECTION_DEFAULT_VALUE;
        this.connectionTimeout = CONNECTION_TIMEOUT_DEFAULT_VALUE;
        this.readTimeout = READ_TIMEOUT_DEFAULT_VALUE;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {

        out.writeOptionalInt(maxConnections);
        out.writeOptionalInt(connectionTimeout);
        out.writeOptionalInt(readTimeout);
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
        return builder.endObject();
    }

    public static ConnectorHttpClientConfig fromStream(StreamInput in) throws IOException {
        ConnectorHttpClientConfig action = new ConnectorHttpClientConfig(in);
        return action;
    }

    public static ConnectorHttpClientConfig parse(XContentParser parser) throws IOException {
        Integer maxConnections = null;
        Integer connectionTimeout = null;
        Integer readTimeout = null;

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
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return ConnectorHttpClientConfig.builder()
                .maxConnections(maxConnections)
                .connectionTimeout(connectionTimeout)
                .readTimeout(readTimeout)
                .build();
    }
}

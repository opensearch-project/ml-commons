/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.time.Instant;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
@EqualsAndHashCode
public class MLConfig implements ToXContentObject, Writeable {

    public static final String TYPE_FIELD = "type";

    public static final String CONFIGURATION_FIELD = "configuration";

    public static final String CREATE_TIME_FIELD = "create_time";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";

    @Setter
    private String type;

    private Configuration configuration;
    private final Instant createTime;
    private Instant lastUpdateTime;

    @Builder(toBuilder = true)
    public MLConfig(
            String type,
            Configuration configuration,
            Instant createTime,
            Instant lastUpdateTime
    ) {
        this.type = type;
        this.configuration = configuration;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
    }

    public MLConfig(StreamInput input) throws IOException {
        this.type = input.readOptionalString();
        if (input.readBoolean()) {
            configuration = new Configuration(input);
        }
        createTime = input.readOptionalInstant();
        lastUpdateTime = input.readOptionalInstant();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(type);
        if (configuration != null) {
            out.writeBoolean(true);
            configuration.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createTime);
        out.writeOptionalInstant(lastUpdateTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (type != null) {
            builder.field(TYPE_FIELD, type);
        }
        if (configuration != null) {
            builder.field(CONFIGURATION_FIELD, configuration);
        }
        if (createTime != null) {
            builder.field(CREATE_TIME_FIELD, createTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATE_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        return builder.endObject();
    }

    public static MLConfig fromStream(StreamInput in) throws IOException {
        MLConfig mlConfig = new MLConfig(in);
        return mlConfig;
    }

    public static MLConfig parse(XContentParser parser) throws IOException {
        String type = null;
        Configuration configuration = null;
        Instant createTime = null;
        Instant lastUpdateTime = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case CONFIGURATION_FIELD:
                    configuration = Configuration.parse(parser);
                    break;
                case CREATE_TIME_FIELD:
                    createTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATE_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLConfig.builder()
                .type(type)
                .configuration(configuration)
                .createTime(createTime)
                .lastUpdateTime(lastUpdateTime)
                .build();
    }
}

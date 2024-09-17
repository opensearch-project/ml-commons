/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;

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
import lombok.Setter;

@Getter
@EqualsAndHashCode
public class MLConfig implements ToXContentObject, Writeable {

    public static final String TYPE_FIELD = "type";

    public static final String CONFIGURATION_FIELD = "configuration";

    public static final String CREATE_TIME_FIELD = "create_time";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";

    // Adding below three new fields since the original fields, type, configuration, and last_update_time
    // are not created with correct data types in config index due to missing schema version bump.
    // Starting 2.15, it is suggested that below fields be used for creating new documents in config index

    public static final String CONFIG_TYPE_FIELD = "config_type";

    public static final String ML_CONFIGURATION_FIELD = "ml_configuration";

    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";

    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_NEW_CONFIG_FIELDS = CommonValue.VERSION_2_15_0;

    @Setter
    private String type;

    @Setter
    private String configType;

    private Configuration configuration;
    private Configuration mlConfiguration;
    private final Instant createTime;
    private Instant lastUpdateTime;
    private Instant lastUpdatedTime;

    @Builder(toBuilder = true)
    public MLConfig(
        String type,
        String configType,
        Configuration configuration,
        Configuration mlConfiguration,
        Instant createTime,
        Instant lastUpdateTime,
        Instant lastUpdatedTime
    ) {
        this.type = type;
        this.configType = configType;
        this.configuration = configuration;
        this.mlConfiguration = mlConfiguration;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public MLConfig(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        this.type = input.readOptionalString();
        if (input.readBoolean()) {
            configuration = new Configuration(input);
        }
        createTime = input.readOptionalInstant();
        lastUpdateTime = input.readOptionalInstant();
        if (streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_NEW_CONFIG_FIELDS)) {
            this.configType = input.readOptionalString();
            if (input.readBoolean()) {
                mlConfiguration = new Configuration(input);
            }
            lastUpdatedTime = input.readOptionalInstant();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalString(type);
        if (configuration != null) {
            out.writeBoolean(true);
            configuration.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createTime);
        out.writeOptionalInstant(lastUpdateTime);
        if (streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_NEW_CONFIG_FIELDS)) {
            out.writeOptionalString(configType);
            if (mlConfiguration != null) {
                out.writeBoolean(true);
                mlConfiguration.writeTo(out);
            } else {
                out.writeBoolean(false);
            }
            out.writeOptionalInstant(lastUpdatedTime);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (configType != null || type != null) {
            builder.field(TYPE_FIELD, configType == null ? type : configType);
        }
        if (configuration != null || mlConfiguration != null) {
            builder.field(CONFIGURATION_FIELD, mlConfiguration == null ? configuration : mlConfiguration);
        }
        if (createTime != null) {
            builder.field(CREATE_TIME_FIELD, createTime.toEpochMilli());
        }
        if (lastUpdateTime != null || lastUpdatedTime != null) {
            builder.field(LAST_UPDATE_TIME_FIELD, lastUpdatedTime == null ? lastUpdateTime.toEpochMilli() : lastUpdatedTime.toEpochMilli());
        }
        return builder.endObject();
    }

    public static MLConfig fromStream(StreamInput in) throws IOException {
        MLConfig mlConfig = new MLConfig(in);
        return mlConfig;
    }

    public static MLConfig parse(XContentParser parser) throws IOException {
        String type = null;
        String configType = null;
        Configuration configuration = null;
        Configuration mlConfiguration = null;
        Instant createTime = null;
        Instant lastUpdateTime = null;
        Instant lastUpdatedTime = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case CONFIG_TYPE_FIELD:
                    configType = parser.text();
                    break;
                case CONFIGURATION_FIELD:
                    configuration = Configuration.parse(parser);
                    break;
                case ML_CONFIGURATION_FIELD:
                    mlConfiguration = Configuration.parse(parser);
                    break;
                case CREATE_TIME_FIELD:
                    createTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATE_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLConfig
            .builder()
            .type(type)
            .configType(configType)
            .configuration(configuration)
            .mlConfiguration(mlConfiguration)
            .createTime(createTime)
            .lastUpdateTime(lastUpdateTime)
            .lastUpdatedTime(lastUpdatedTime)
            .build();
    }
}

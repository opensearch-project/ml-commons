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
import static org.opensearch.ml.common.CommonValue.TENANT_ID;

@Getter
@EqualsAndHashCode
public class MLConfig implements ToXContentObject, Writeable {

    public static final String TYPE_FIELD = "type";

    public static final String CONFIG_TYPE_FIELD = "config_type";

    public static final String CONFIGURATION_FIELD = "configuration";

    public static final String ML_CONFIGURATION_FIELD = "ml_configuration";

    public static final String CREATE_TIME_FIELD = "create_time";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";

    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";


    @Setter
    private String type;

    private Configuration configuration;
    private final Instant createTime;
    private final Instant lastUpdateTime;
    private final String tenantId;

    @Builder(toBuilder = true)
    public MLConfig(
            String type,
            Configuration configuration,
            Instant createTime,
            Instant lastUpdateTime,
            String tenantId
    ) {
        this.type = type;
        this.configuration = configuration;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
        this.tenantId = tenantId;
    }

    public MLConfig(StreamInput input) throws IOException {
        this.type = input.readOptionalString();
        if (input.readBoolean()) {
            configuration = new Configuration(input);
        }
        createTime = input.readOptionalInstant();
        lastUpdateTime = input.readOptionalInstant();
        //TODO: Check BWC later
        tenantId = input.readOptionalString();
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
        //TODO: check BWC later
        out.writeOptionalString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (type != null) {
            builder.field(CONFIG_TYPE_FIELD, type);
        }
        if (configuration != null) {
            builder.field(ML_CONFIGURATION_FIELD, configuration);
        }
        if (createTime != null) {
            builder.field(CREATE_TIME_FIELD, createTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATE_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (tenantId != null) {
            builder.field(TENANT_ID, tenantId);
        }
        return builder.endObject();
    }

    public static MLConfig fromStream(StreamInput in) throws IOException {
        return new MLConfig(in);
    }

    public static MLConfig parse(XContentParser parser) throws IOException {
        String type = null;
        String configType = null;
        Configuration configuration = null;
        Configuration mlConfiguration = null;
        Instant createTime = null;
        Instant lastUpdateTime = null;
        Instant lastUpdatedTime = null;
        String tenantId = null;

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
                case TENANT_ID:
                    tenantId = parser.textOrNull();
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLConfig.builder()
                .type(configType == null ? type : configType)
                .configuration(mlConfiguration == null ? configuration : mlConfiguration)
                .createTime(createTime)
                .lastUpdateTime(lastUpdatedTime == null ? lastUpdateTime : lastUpdatedTime)
                .tenantId(tenantId)
                .build();
    }
}

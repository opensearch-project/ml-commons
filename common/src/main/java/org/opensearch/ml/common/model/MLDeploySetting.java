/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.Version;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;

import java.io.IOException;
import java.time.Duration;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Setter
@Getter
public class MLDeploySetting implements ToXContentObject, Writeable {
    public static final String IS_AUTO_DEPLOY_ENABLED_FIELD = "is_auto_deploy_enabled";
    public static final String MODEL_TTL_HOURS_FIELD = "model_ttl_hours";
    public static final String MODEL_TTL_MINUTES_FIELD = "model_ttl_minutes";
    private static final long DEFAULT_TTL_HOUR = -1;
    private static final long DEFAULT_TTL_MINUTES = -1;

    private Boolean isAutoDeployEnabled;
    private Long modelTTLInHours;  // Time to live in hours
    private Long modelTTLInMinutes; // in minutes

    @Builder(toBuilder = true)
    public MLDeploySetting(Boolean isAutoDeployEnabled, Long modelTTLInHours, Long modelTTLInMinutes) {
        this.isAutoDeployEnabled = isAutoDeployEnabled;
        this.modelTTLInHours = modelTTLInHours;
        this.modelTTLInMinutes = modelTTLInMinutes;
        if (modelTTLInHours == null && modelTTLInMinutes == null) {
            this.modelTTLInHours = DEFAULT_TTL_HOUR;
            this.modelTTLInMinutes = DEFAULT_TTL_MINUTES;
            return;
        }
        if (modelTTLInHours == null) {
            this.modelTTLInHours = 0L;
        }
        if (modelTTLInMinutes == null) {
            this.modelTTLInMinutes = 0L;
        }
    }

    public MLDeploySetting(StreamInput in) throws IOException {
        this.isAutoDeployEnabled = in.readOptionalBoolean();
        Version streamInputVersion = in.getVersion();
        if (streamInputVersion.onOrAfter(MLSyncUpInput.MINIMAL_SUPPORTED_VERSION_FOR_MODEL_TTL)) {
            this.modelTTLInHours = in.readOptionalLong();
            this.modelTTLInMinutes = in.readOptionalLong();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalBoolean(isAutoDeployEnabled);
        if (streamOutputVersion.onOrAfter(MLSyncUpInput.MINIMAL_SUPPORTED_VERSION_FOR_MODEL_TTL)) {
            out.writeOptionalLong(modelTTLInHours);
            out.writeOptionalLong(modelTTLInMinutes);
        }
    }

    public static MLDeploySetting parse(XContentParser parser) throws IOException {
        Boolean isAutoDeployEnabled = null;
        Long modelTTLHours = null;
        Long modelTTLMinutes = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case IS_AUTO_DEPLOY_ENABLED_FIELD:
                    isAutoDeployEnabled = parser.booleanValue();
                    break;
                case MODEL_TTL_HOURS_FIELD:
                    modelTTLHours = parser.longValue();
                case MODEL_TTL_MINUTES_FIELD:
                    modelTTLMinutes = parser.longValue();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLDeploySetting(isAutoDeployEnabled, modelTTLHours, modelTTLMinutes);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (isAutoDeployEnabled != null) {
            builder.field(IS_AUTO_DEPLOY_ENABLED_FIELD, isAutoDeployEnabled);
        }
        if (modelTTLInHours != null) {
            builder.field(MODEL_TTL_HOURS_FIELD, modelTTLInHours);
        }
        if (modelTTLInMinutes != null) {
            builder.field(MODEL_TTL_MINUTES_FIELD, modelTTLInMinutes);
        }
        builder.endObject();
        return builder;
    }
}

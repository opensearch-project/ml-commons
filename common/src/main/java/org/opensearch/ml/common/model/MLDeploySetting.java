/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.time.Duration;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Setter
@Getter
public class MLDeploySetting implements ToXContentObject, Writeable {
    public static final String IS_AUTO_DEPLOY_ENABLED_FIELD = "is_auto_deploy_enabled";
    public static final String MODEL_TTL_FIELD = "model_ttl";
    private static final long DEFAULT_TTL_HOUR = -1;

    private Boolean isAutoDeployEnabled;
    private Long modelTTL;  // Time to live in hours

    @Builder(toBuilder = true)
    public MLDeploySetting(Boolean isAutoDeployEnabled) {
        this.isAutoDeployEnabled = isAutoDeployEnabled;
        this.modelTTL = DEFAULT_TTL_HOUR;
    }
    @Builder(toBuilder = true)
    public MLDeploySetting(Boolean isAutoDeployEnabled, Long modelTTL) {
        this.isAutoDeployEnabled = isAutoDeployEnabled;
        this.modelTTL = modelTTL;
    }

    public MLDeploySetting(StreamInput in) throws IOException {
        this.isAutoDeployEnabled = in.readOptionalBoolean();
        this.modelTTL = in.readOptionalLong();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalBoolean(isAutoDeployEnabled);
        out.writeOptionalLong(modelTTL);
    }

    public static MLDeploySetting parse(XContentParser parser) throws IOException {
        Boolean isAutoDeployEnabled = null;
        Long modelTTL = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case IS_AUTO_DEPLOY_ENABLED_FIELD:
                    isAutoDeployEnabled = parser.booleanValue();
                    break;
                case MODEL_TTL_FIELD:
                    modelTTL = parser.longValue();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLDeploySetting(isAutoDeployEnabled, modelTTL);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (isAutoDeployEnabled != null) {
            builder.field(IS_AUTO_DEPLOY_ENABLED_FIELD, isAutoDeployEnabled);
        }
        if (modelTTL != null) {
            builder.field(MODEL_TTL_FIELD, modelTTL);
        }
        builder.endObject();
        return builder;
    }
}

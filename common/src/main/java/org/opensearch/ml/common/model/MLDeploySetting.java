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

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Setter
@Getter
public class MLDeploySetting implements ToXContentObject, Writeable {
    public static final String IS_AUTO_DEPLOY_ENABLED_FIELD = "is_auto_deploy_enabled";

    private Boolean isAutoDeployEnabled;

    @Builder(toBuilder = true)
    public MLDeploySetting(Boolean isAutoDeployEnabled) {
        this.isAutoDeployEnabled = isAutoDeployEnabled;
    }

    public MLDeploySetting(StreamInput in) throws IOException {
        this.isAutoDeployEnabled = in.readOptionalBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalBoolean(isAutoDeployEnabled);
    }

    public static MLDeploySetting parse(XContentParser parser) throws IOException {
        Boolean isAutoDeployEnabled = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case IS_AUTO_DEPLOY_ENABLED_FIELD:
                    isAutoDeployEnabled = parser.booleanValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLDeploySetting(isAutoDeployEnabled);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (isAutoDeployEnabled != null) {
            builder.field(IS_AUTO_DEPLOY_ENABLED_FIELD, isAutoDeployEnabled);
        }
        builder.endObject();
        return builder;
    }
}

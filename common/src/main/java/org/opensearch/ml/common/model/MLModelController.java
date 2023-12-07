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
import java.util.concurrent.TimeUnit;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Setter
@Getter
public class MLModelController implements ToXContentObject, Writeable {
    public static final String IS_REQUEST_ACCEPTED = "is_request_accepted";
    public static final String RATE_LIMIT_NUMBER_FIELD = "rate_limit_number";
    public static final String RATE_LIMIT_UNIT_FIELD = "rate_limit_unit";

    private Boolean isRequestAccepted;
    private Integer rateLimitNumber;
    private TimeUnit rateLimitUnit;

    @Builder(toBuilder = true)
    public MLModelController(Boolean isRequestAccepted, Integer rateLimitNumber, TimeUnit rateLimitUnit) {
        this.isRequestAccepted = isRequestAccepted;
        this.rateLimitNumber = rateLimitNumber;
        this.rateLimitUnit = rateLimitUnit;
    }

    public static MLModelController parse(XContentParser parser) throws IOException {
        Boolean isRequestAccepted = null;
        Integer rateLimitNumber = null;
        TimeUnit rateLimitUnit = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case IS_REQUEST_ACCEPTED:
                    isRequestAccepted = parser.booleanValue();
                    break;
                case RATE_LIMIT_NUMBER_FIELD:
                    rateLimitNumber = parser.intValue();
                    break;
                case RATE_LIMIT_UNIT_FIELD:
                    rateLimitUnit = TimeUnit.valueOf(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLModelController(isRequestAccepted, rateLimitNumber, rateLimitUnit);
    }

    public MLModelController(StreamInput in) throws IOException{
        this.isRequestAccepted = in.readOptionalBoolean();
        this.rateLimitNumber = in.readOptionalInt();
        if (in.readBoolean()) {
            this.rateLimitUnit = in.readEnum(TimeUnit.class);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalBoolean(isRequestAccepted);
        out.writeOptionalInt(rateLimitNumber);
        if (rateLimitUnit != null) {
            out.writeBoolean(true);
            out.writeEnum(rateLimitUnit);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (isRequestAccepted != null) {
            builder.field(IS_REQUEST_ACCEPTED, isRequestAccepted);
        }
        if (rateLimitNumber != null) {
            builder.field(RATE_LIMIT_NUMBER_FIELD, rateLimitNumber);
        }
        if (rateLimitUnit != null) {
            builder.field(RATE_LIMIT_UNIT_FIELD, rateLimitUnit);
        }
        builder.endObject();
        return builder;
    }

    public void update(MLModelController updateContent) {
        if (updateContent.getIsRequestAccepted() != null) {
            this.isRequestAccepted = updateContent.getIsRequestAccepted();
        }
        if (updateContent.getRateLimitNumber() != null) {
            this.rateLimitNumber = updateContent.getRateLimitNumber();
        }
        if (updateContent.getRateLimitUnit() != null) {
            this.rateLimitUnit = updateContent.getRateLimitUnit();
        }
    }
}

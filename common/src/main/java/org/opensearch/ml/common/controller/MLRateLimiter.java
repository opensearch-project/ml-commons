/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.controller;

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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Setter
@Getter
public class MLRateLimiter implements ToXContentObject, Writeable {
    public static final String RATE_LIMIT_NUMBER_FIELD = "rate_limit_number";
    public static final String RATE_LIMIT_UNIT_FIELD = "rate_limit_unit";

    private String rateLimitNumber;
    private TimeUnit rateLimitUnit;

    @Builder(toBuilder = true)
    public MLRateLimiter(String rateLimitNumber, TimeUnit rateLimitUnit) {
        this.rateLimitNumber = rateLimitNumber;
        this.rateLimitUnit = rateLimitUnit;
    }

    public static MLRateLimiter parse(XContentParser parser) throws IOException {
        String rateLimitNumber = null;
        TimeUnit rateLimitUnit = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case RATE_LIMIT_NUMBER_FIELD:
                    rateLimitNumber = parser.text();
                    break;
                case RATE_LIMIT_UNIT_FIELD:
                    rateLimitUnit = TimeUnit.valueOf(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRateLimiter(rateLimitNumber, rateLimitUnit);
    }

    public MLRateLimiter(StreamInput in) throws IOException{
        this.rateLimitNumber = in.readOptionalString();
        if (in.readBoolean()) {
            this.rateLimitUnit = in.readEnum(TimeUnit.class);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(rateLimitNumber);
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
        if (rateLimitNumber != null) {
            builder.field(RATE_LIMIT_NUMBER_FIELD, rateLimitNumber);
        }
        if (rateLimitUnit != null) {
            builder.field(RATE_LIMIT_UNIT_FIELD, rateLimitUnit);
        }
        builder.endObject();
        return builder;
    }

    public void update(MLRateLimiter updateContent) {
        if (updateContent.getRateLimitNumber() != null) {
            this.rateLimitNumber = updateContent.getRateLimitNumber();
        }
        if (updateContent.getRateLimitUnit() != null) {
            this.rateLimitUnit = updateContent.getRateLimitUnit();
        }
    }

    public static boolean canUpdate(MLRateLimiter rateLimiter, MLRateLimiter updateContent) {
        if (updateContent == null) {
            return false;
        } else if (rateLimiter == null) {
            return true;
        } else
        return !Objects.equals(updateContent.getRateLimitNumber(), rateLimiter.rateLimitNumber) || !Objects.equals(updateContent.getRateLimitUnit(), rateLimiter.rateLimitUnit);
    }

    public static boolean isRateLimiterConstructable(MLRateLimiter rateLimiter) {
        return (rateLimiter.getRateLimitUnit() != null && rateLimiter.getRateLimitNumber() != null);
    }
}

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

    public static MLRateLimiter update(MLRateLimiter rateLimiter, MLRateLimiter updateContent) {
        if (rateLimiter == null) {
            return updateContent;
        } else {
            rateLimiter.update(updateContent);
            return rateLimiter;
        }
    }

    /**
     * Checks the validity of this incoming update before performing an update operation.
     * A valid update indicates the corresponding index will be updated with the current MLRateLimiter config and the update content
     *
     * @param rateLimiter    The existing rate limiter.
     * @param updateContent  The update content.
     * @return true if the update is valid, false otherwise.
     */
    public static boolean updateValidityPreCheck(MLRateLimiter rateLimiter, MLRateLimiter updateContent) {
        if (updateContent == null) {
            return false;
        } else if (rateLimiter == null) {
            return true;
        } else if (updateContent.isEmpty()) {
            return false;
        } else return (!Objects.equals(updateContent.getRateLimitNumber(), rateLimiter.getRateLimitNumber()) && updateContent.getRateLimitNumber() != null)
                || (!Objects.equals(updateContent.getRateLimitUnit(), rateLimiter.getRateLimitUnit()) &&  updateContent.getRateLimitUnit() != null);
    }

    /**
     * Checks if we need to deploy this update into ML Cache (if model is deployed) after performing this update operation.
     *
     * @param rateLimiter    The existing rate limiter.
     * @param updateContent  The update content.
     * @return true if the update is valid, false otherwise.
     */
    public static boolean isDeployRequiredAfterUpdate(MLRateLimiter rateLimiter, MLRateLimiter updateContent) {
        if (!updateValidityPreCheck(rateLimiter, updateContent)) {
            return false;
        } else {
            return updateContent.isValid()
                    || (rateLimiter.getRateLimitUnit() != null && updateContent.getRateLimitNumber() != null)
                    || (rateLimiter.getRateLimitNumber() != null && updateContent.getRateLimitUnit() != null);
        }
    }

    public boolean isValid() {
        return (this.rateLimitUnit != null && this.rateLimitNumber != null);
    }

    public boolean isEmpty() {
        return (this.rateLimitUnit == null && this.rateLimitNumber == null);
    }
}

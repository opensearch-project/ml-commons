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
    public static final String LIMIT_FIELD = "limit";
    public static final String UNIT_FIELD = "unit";

    private String limit;
    private TimeUnit unit;

    @Builder(toBuilder = true)
    public MLRateLimiter(String limit, TimeUnit unit) {
        this.limit = limit;
        this.unit = unit;
    }

    public static MLRateLimiter parse(XContentParser parser) throws IOException {
        String limit = null;
        TimeUnit unit = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case LIMIT_FIELD:
                    limit = parser.text();
                    break;
                case UNIT_FIELD:
                    unit = TimeUnit.valueOf(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRateLimiter(limit, unit);
    }

    public MLRateLimiter(StreamInput in) throws IOException {
        this.limit = in.readOptionalString();
        if (in.readBoolean()) {
            this.unit = in.readEnum(TimeUnit.class);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(limit);
        if (unit != null) {
            out.writeBoolean(true);
            out.writeEnum(unit);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (limit != null) {
            builder.field(LIMIT_FIELD, limit);
        }
        if (unit != null) {
            builder.field(UNIT_FIELD, unit);
        }
        builder.endObject();
        return builder;
    }

    public void update(MLRateLimiter updateContent) {
        if (updateContent.getLimit() != null) {
            this.limit = updateContent.getLimit();
        }
        if (updateContent.getUnit() != null) {
            this.unit = updateContent.getUnit();
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
     * Checks the validity of this incoming update before performing an update
     * operation.
     * A valid update indicates the corresponding index will be updated with the
     * current MLRateLimiter config and the update content
     *
     * @param rateLimiter   The existing rate limiter.
     * @param updateContent The update content.
     * @return true if the update is valid, false otherwise.
     */
    public static boolean updateValidityPreCheck(MLRateLimiter rateLimiter, MLRateLimiter updateContent) {
        if (updateContent == null) {
            return false;
        } else if (rateLimiter == null) {
            return true;
        } else if (updateContent.isEmpty()) {
            return false;
        } else
            return (!Objects.equals(updateContent.getLimit(), rateLimiter.getLimit())
                    && updateContent.getLimit() != null)
                    || (!Objects.equals(updateContent.getUnit(), rateLimiter.getUnit())
                            && updateContent.getUnit() != null);
    }

    /**
     * Checks if we need to deploy this update into ML Cache (if model is deployed)
     * after performing this update operation.
     *
     * @param rateLimiter   The existing rate limiter.
     * @param updateContent The update content.
     * @return true if the update is valid, false otherwise.
     */
    public static boolean isDeployRequiredAfterUpdate(MLRateLimiter rateLimiter, MLRateLimiter updateContent) {
        if (!updateValidityPreCheck(rateLimiter, updateContent)) {
            return false;
        } else {
            return updateContent.isValid()
                    || (rateLimiter.getUnit() != null && updateContent.getLimit() != null)
                    || (rateLimiter.getLimit() != null && updateContent.getUnit() != null);
        }
    }

    public boolean isValid() {
        return (this.unit != null && this.limit != null);
    }

    public boolean isEmpty() {
        return (this.unit == null && this.limit == null);
    }
}

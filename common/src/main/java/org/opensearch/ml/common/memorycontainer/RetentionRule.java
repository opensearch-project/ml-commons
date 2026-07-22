/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MAX_COUNT_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.RETENTION_DAYS_FIELD;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class RetentionRule implements ToXContentObject, Writeable {

    private final Integer retentionDays;
    private final Integer maxCount;

    /**
     * Transient parse-time metadata: true when retention_days was explicitly present in the
     * parsed JSON (even if value was null, meaning "remove it"). Not serialized.
     */
    @EqualsAndHashCode.Exclude
    private final transient boolean retentionDaysExplicitlySet;

    /**
     * Transient parse-time metadata: true when max_count was explicitly present in the
     * parsed JSON (even if value was null, meaning "remove it"). Not serialized.
     */
    @EqualsAndHashCode.Exclude
    private final transient boolean maxCountExplicitlySet;

    @Builder
    public RetentionRule(Integer retentionDays, Integer maxCount) {
        this(retentionDays, maxCount, false, false);
    }

    public RetentionRule(Integer retentionDays, Integer maxCount, boolean retentionDaysExplicitlySet, boolean maxCountExplicitlySet) {
        if (retentionDays != null && retentionDays <= 0) {
            throw new IllegalArgumentException("retention_days must be a positive integer or null");
        }
        if (maxCount != null && maxCount <= 0) {
            throw new IllegalArgumentException("max_count must be a positive integer or null");
        }
        this.retentionDays = retentionDays;
        this.maxCount = maxCount;
        this.retentionDaysExplicitlySet = retentionDaysExplicitlySet;
        this.maxCountExplicitlySet = maxCountExplicitlySet;
    }

    public RetentionRule(StreamInput in) throws IOException {
        this.retentionDays = in.readOptionalInt();
        this.maxCount = in.readOptionalInt();
        this.retentionDaysExplicitlySet = false;
        this.maxCountExplicitlySet = false;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalInt(retentionDays);
        out.writeOptionalInt(maxCount);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (retentionDays != null) {
            builder.field(RETENTION_DAYS_FIELD, retentionDays);
        } else if (retentionDaysExplicitlySet) {
            // Persist the explicit null so the partial-update doc merge removes the stored value
            builder.nullField(RETENTION_DAYS_FIELD);
        }
        if (maxCount != null) {
            builder.field(MAX_COUNT_FIELD, maxCount);
        } else if (maxCountExplicitlySet) {
            builder.nullField(MAX_COUNT_FIELD);
        }
        builder.endObject();
        return builder;
    }

    public static RetentionRule parse(XContentParser parser) throws IOException {
        Integer retentionDays = null;
        Integer maxCount = null;
        boolean retentionDaysExplicitlySet = false;
        boolean maxCountExplicitlySet = false;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case RETENTION_DAYS_FIELD:
                    retentionDaysExplicitlySet = true;
                    if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                        retentionDays = null;
                    } else {
                        retentionDays = parser.intValue();
                    }
                    break;
                case MAX_COUNT_FIELD:
                    maxCountExplicitlySet = true;
                    if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                        maxCount = null;
                    } else {
                        maxCount = parser.intValue();
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new RetentionRule(retentionDays, maxCount, retentionDaysExplicitlySet, maxCountExplicitlySet);
    }
}

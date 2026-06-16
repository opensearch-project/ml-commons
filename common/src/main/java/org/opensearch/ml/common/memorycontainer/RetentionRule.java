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
@Builder
@EqualsAndHashCode
public class RetentionRule implements ToXContentObject, Writeable {

    private final Integer retentionDays;
    private final Integer maxCount;

    public RetentionRule(Integer retentionDays, Integer maxCount) {
        if (retentionDays != null && retentionDays <= 0) {
            throw new IllegalArgumentException("retention_days must be a positive integer or null");
        }
        if (maxCount != null && maxCount <= 0) {
            throw new IllegalArgumentException("max_count must be a positive integer or null");
        }
        this.retentionDays = retentionDays;
        this.maxCount = maxCount;
    }

    public RetentionRule(StreamInput in) throws IOException {
        this.retentionDays = in.readOptionalInt();
        this.maxCount = in.readOptionalInt();
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
        }
        if (maxCount != null) {
            builder.field(MAX_COUNT_FIELD, maxCount);
        }
        builder.endObject();
        return builder;
    }

    public static RetentionRule parse(XContentParser parser) throws IOException {
        Integer retentionDays = null;
        Integer maxCount = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case RETENTION_DAYS_FIELD:
                    if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                        retentionDays = null;
                    } else {
                        retentionDays = parser.intValue();
                    }
                    break;
                case MAX_COUNT_FIELD:
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

        return new RetentionRule(retentionDays, maxCount);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

import org.opensearch.common.xcontent.XContentFactory;
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

@EqualsAndHashCode
@Getter
public class IndexInsight implements ToXContentObject, Writeable {
    public static final String INDEX_NAME_FIELD = "index_name";
    public static final String LAST_UPDATE_FIELD = "last_updated_time";
    public static final String CONTENT_FIELD = "content";
    public static final String STATUS_FIELD = "status";
    public static final String TASK_TYPE_FIELD = "task_type";

    private final String index;
    private final String content;
    private final IndexInsightTaskStatus status;
    private final MLIndexInsightType taskType;
    private final Instant lastUpdatedTime;
    private final String tenantId;

    @Builder(toBuilder = true)
    public IndexInsight(
        String index,
        String content,
        IndexInsightTaskStatus status,
        MLIndexInsightType taskType,
        Instant lastUpdatedTime,
        String tenantId
    ) {
        this.index = index;
        this.content = content;
        this.status = status;
        this.taskType = taskType;
        this.lastUpdatedTime = lastUpdatedTime;
        this.tenantId = tenantId;
    }

    public IndexInsight(StreamInput input) throws IOException {
        index = input.readString();
        status = IndexInsightTaskStatus.fromString(input.readString());
        taskType = MLIndexInsightType.fromString(input.readString());
        lastUpdatedTime = input.readInstant();
        content = input.readOptionalString();
        tenantId = input.readOptionalString();
    }

    public static IndexInsight parse(XContentParser parser) throws IOException {
        String indexName = null;
        String content = null;
        IndexInsightTaskStatus status = null;
        String taskType = null;
        Instant lastUpdatedTime = null;
        String tenantId = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case INDEX_NAME_FIELD:
                    indexName = parser.text();
                    break;
                case CONTENT_FIELD:
                    content = parser.text();
                    break;
                case STATUS_FIELD:
                    status = IndexInsightTaskStatus.fromString(parser.text());
                    break;
                case TASK_TYPE_FIELD:
                    taskType = parser.text();
                    break;
                case LAST_UPDATE_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return IndexInsight
            .builder()
            .index(indexName)
            .content(content)
            .status(status)
            .taskType(MLIndexInsightType.fromString(taskType))
            .lastUpdatedTime(lastUpdatedTime)
            .tenantId(tenantId)
            .build();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(index);
        out.writeString(status.toString());
        out.writeString(taskType.toString());
        out.writeInstant(lastUpdatedTime);
        out.writeOptionalString(content);
        out.writeOptionalString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (index != null) {
            builder.field(INDEX_NAME_FIELD, index);
        }
        if (content != null && !content.isEmpty()) {
            builder.field(CONTENT_FIELD, content);
        }
        if (status != null) {
            builder.field(STATUS_FIELD, status.toString());
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.field(TASK_TYPE_FIELD, taskType.toString());
        builder.field(LAST_UPDATE_FIELD, lastUpdatedTime.toEpochMilli());
        builder.endObject();
        return builder;
    }

    public static IndexInsight fromStream(StreamInput in) throws IOException {
        return new IndexInsight(in);
    }

    @Override
    public String toString() {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.prettyPrint();
            toXContent(builder, ToXContent.EMPTY_PARAMS);
            return builder.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
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

    private String index;
    private String content;
    private IndexInsightTaskStatus status;
    private MLIndexInsightType taskType;
    private Instant lastUpdatedTime;

    @Builder(toBuilder = true)
    public IndexInsight(String index, String content, IndexInsightTaskStatus status, MLIndexInsightType taskType, Instant lastUpdatedTime) {
        this.index = index;
        this.content = content;
        this.status = status;
        this.taskType = taskType;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public IndexInsight(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        index = input.readString();
        if (input.readBoolean()) {
            content = input.readString();
        }
        if (input.readBoolean()) {
            status = IndexInsightTaskStatus.fromString(input.readString());
        }
        taskType = MLIndexInsightType.fromString(input.readString());

        lastUpdatedTime = Instant.ofEpochMilli(input.readLong());

    }

    public static IndexInsight parse(XContentParser parser) throws IOException {
        String indexName = null;
        String content = null;
        IndexInsightTaskStatus status = null;
        String taskType = null;
        Instant lastUpdatedTime = null;
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
            .build();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(index);
        if (content != null && !content.isEmpty()) {
            out.writeBoolean(true);
            out.writeString(content);
        } else {
            out.writeBoolean(false);
        }
        if (status != null) {
            out.writeBoolean(true);
            out.writeString(status.toString());
        } else {
            out.writeBoolean(false);
        }
        out.writeString(taskType.toString());
        out.writeInstant(lastUpdatedTime);
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
        builder.field(TASK_TYPE_FIELD, taskType.toString());
        builder.field(LAST_UPDATE_FIELD, lastUpdatedTime.toEpochMilli());
        builder.endObject();
        return builder;
    }

    public static IndexInsight fromStream(StreamInput in) throws IOException {
        return new IndexInsight(in);
    }
}

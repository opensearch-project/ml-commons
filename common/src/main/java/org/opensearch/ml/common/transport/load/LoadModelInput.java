/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.load;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.MLTask;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class LoadModelInput implements ToXContentObject, Writeable {
    private static final String MODEL_ID_FIELD = "model_id";
    private static final String TASK_ID_FIELD = "task_id";
    private static final String MODEL_CONTENT_HASH_FIELD = "model_content_hash";
    private static final String NODE_COUNT_FIELD = "node_count";
    private static final String COORDINATING_NODE_ID_FIELD = "coordinating_node_id";
    private static final String ML_TASK_FIELD = "task";
    private String modelId;
    private String taskId;
    private String modelContentHash;
    private Integer nodeCount;
    private String coordinatingNodeId;
    private MLTask mlTask;

    public LoadModelInput(StreamInput in) throws IOException {
        this.modelId = in.readString();
        this.taskId = in.readString();
        this.modelContentHash = in.readOptionalString();
        this.nodeCount = in.readInt();
        this.coordinatingNodeId = in.readString();
        this.mlTask = new MLTask(in);
    }

    @Builder
    public LoadModelInput(String modelId, String taskId, String modelContentHash, Integer nodeCount, String coordinatingNodeId, MLTask mlTask) {
        this.modelId = modelId;
        this.taskId = taskId;
        this.modelContentHash = modelContentHash;
        this.nodeCount = nodeCount;
        this.coordinatingNodeId = coordinatingNodeId;
        this.mlTask = mlTask;
    }

    public LoadModelInput() {

    }

    public static LoadModelInput parse(XContentParser parser) throws IOException {
        String modelId = null;
        String taskId = null;
        String contentHash = null;
        Integer nodeCount = null;
        String coordinatingNodeId = null;
        MLTask mlTask = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case TASK_ID_FIELD:
                    taskId = parser.text();
                    break;
                case MODEL_CONTENT_HASH_FIELD:
                    contentHash = parser.text();
                    break;
                case NODE_COUNT_FIELD:
                    nodeCount = parser.intValue();
                    break;
                case COORDINATING_NODE_ID_FIELD:
                    coordinatingNodeId = parser.text();
                    break;
                case ML_TASK_FIELD:
                    mlTask = MLTask.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new LoadModelInput(modelId, taskId, contentHash, nodeCount, coordinatingNodeId, mlTask);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeString(taskId);
        out.writeOptionalString(modelContentHash);
        out.writeInt(nodeCount);
        out.writeString(coordinatingNodeId);
        mlTask.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        builder.field(TASK_ID_FIELD, taskId);
        builder.field(MODEL_CONTENT_HASH_FIELD, modelContentHash);
        builder.field(NODE_COUNT_FIELD, nodeCount);
        builder.field(COORDINATING_NODE_ID_FIELD, coordinatingNodeId);
        builder.field(ML_TASK_FIELD, mlTask);
        builder.endObject();
        return builder;
    }
}

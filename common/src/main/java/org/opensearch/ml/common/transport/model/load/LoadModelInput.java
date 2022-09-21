/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.load;

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
    private static final String NAME_FIELD = "model_names";
    private static final String TASK_ID_FIELD = "task_id";
    private static final String NODE_COUNT_FIELD = "node_count";
    private static final String COORDINATING_NODE_ID_FIELD = "coordinating_node_id";
    private static final String ML_TASK_FIELD = "task";
    private String modelId;
    private String taskId;
    private Integer nodeCount;
    private String coordinatingNodeId;
    private MLTask mlTask;

    public LoadModelInput(StreamInput in) throws IOException {
        this.modelId = in.readString();
        this.taskId = in.readString();
        this.nodeCount = in.readInt();
        this.coordinatingNodeId = in.readString();
        this.mlTask = new MLTask(in);
    }

    @Builder
    public LoadModelInput(String modelName, String taskId, Integer nodeCount, String coordinatingNodeId, MLTask mlTask) {
        this.modelId = modelName;
        this.taskId = taskId;
        this.nodeCount = nodeCount;
        this.coordinatingNodeId = coordinatingNodeId;
        this.mlTask = mlTask;
    }

    public LoadModelInput() {

    }

    public static LoadModelInput parse(XContentParser parser) throws IOException {
        String modelName = null;
        String taskId = null;
        Integer nodeCount = null;
        String coordinatingNodeId = null;
        MLTask mlTask = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    modelName = parser.text();
                    break;
                case TASK_ID_FIELD:
                    taskId = parser.text();
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
        return new LoadModelInput(modelName, taskId, nodeCount, coordinatingNodeId, mlTask);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeString(taskId);
        out.writeInt(nodeCount);
        out.writeString(coordinatingNodeId);
        mlTask.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, modelId);
        builder.field(TASK_ID_FIELD, taskId);
        builder.field(NODE_COUNT_FIELD, nodeCount);
        builder.field(COORDINATING_NODE_ID_FIELD, coordinatingNodeId);
        builder.field(ML_TASK_FIELD, mlTask);
        builder.endObject();
        return builder;
    }
}

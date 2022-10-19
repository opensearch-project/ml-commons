/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.profile;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.utils.MLNodeUtils.parseArrayField;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

@Getter
public class MLProfileInput implements ToXContentObject, Writeable {
    public static final String MODELS = "model_ids";
    public static final String TASKS = "task_ids";
    public static final String NODE_IDS = "node_ids";
    public static final String RETURN_ALL_TASKS = "return_all_tasks";
    public static final String RETURN_ALL_MODELS = "return_all_models";

    /**
     * Which models profiles will be retrieved
     */
    private Set<String> modelIds;

    /**
     * Which tasks profiles will be retrieved
     */
    private Set<String> taskIds;
    /**
     * Which node's profile will be retrieved.
     */
    private Set<String> nodeIds;
    /**
     * Should return all tasks in cache or not
     */
    @Setter
    private boolean returnAllTasks;
    @Setter
    private boolean returnAllModels;

    /**
     * Constructor
     * @param modelIds
     * @param taskIds
     */
    @Builder
    public MLProfileInput(Set<String> modelIds, Set<String> taskIds, Set<String> nodeIds, boolean returnAllTasks, boolean returnAllModels) {
        this.modelIds = modelIds;
        this.taskIds = taskIds;
        this.nodeIds = nodeIds;
        this.returnAllTasks = returnAllTasks;
        this.returnAllModels = returnAllModels;
    }

    public MLProfileInput() {
        this.modelIds = new HashSet<>();
        this.taskIds = new HashSet<>();
        this.nodeIds = new HashSet<>();
        returnAllTasks = false;
        returnAllModels = false;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalStringCollection(modelIds);
        out.writeOptionalStringCollection(taskIds);
        out.writeOptionalStringCollection(nodeIds);
        out.writeBoolean(returnAllTasks);
        out.writeBoolean(returnAllModels);
    }

    public MLProfileInput(StreamInput input) throws IOException {
        modelIds = input.readBoolean() ? new HashSet<>(input.readStringList()) : new HashSet<>();
        taskIds = input.readBoolean() ? new HashSet<>(input.readStringList()) : new HashSet<>();
        nodeIds = input.readBoolean() ? new HashSet<>(input.readStringList()) : new HashSet<>();
        this.returnAllTasks = input.readBoolean();
        this.returnAllModels = input.readBoolean();
    }

    public static MLProfileInput parse(XContentParser parser) throws IOException {
        Set<String> modelIds = new HashSet<>();
        Set<String> taskIds = new HashSet<>();
        Set<String> nodeIds = new HashSet<>();
        boolean returnALlTasks = false;
        boolean returnAllModels = false;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODELS:
                    parseArrayField(parser, modelIds);
                    break;
                case TASKS:
                    parseArrayField(parser, taskIds);
                    break;
                case NODE_IDS:
                    parseArrayField(parser, nodeIds);
                    break;
                case RETURN_ALL_TASKS:
                    returnALlTasks = parser.booleanValue();
                    break;
                case RETURN_ALL_MODELS:
                    returnAllModels = parser.booleanValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLProfileInput
            .builder()
            .modelIds(modelIds)
            .taskIds(taskIds)
            .nodeIds(nodeIds)
            .returnAllTasks(returnALlTasks)
            .returnAllModels(returnAllModels)
            .build();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (modelIds != null) {
            builder.field(MODELS, modelIds);
        }
        if (taskIds != null) {
            builder.field(TASKS, taskIds);
        }
        if (nodeIds != null) {
            builder.field(NODE_IDS, nodeIds);
        }
        builder.field(RETURN_ALL_TASKS, returnAllTasks);
        builder.field(RETURN_ALL_MODELS, returnAllModels);
        builder.endObject();
        return builder;
    }

    public boolean retrieveProfileOnAllNodes() {
        return nodeIds == null || nodeIds.size() == 0;
    }

    public boolean emptyTasks() {
        return taskIds == null || taskIds.size() == 0;
    }

    public boolean emptyModels() {
        return modelIds == null || modelIds.size() == 0;
    }
}

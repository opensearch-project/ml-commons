/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.dataset.MLInputDataType;

import java.io.IOException;
import java.time.Instant;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
@EqualsAndHashCode
public class MLTask implements ToXContentObject, Writeable {

    public static final String TASK_ID_FIELD = "task_id";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String TASK_TYPE_FIELD = "task_type";
    public static final String FUNCTION_NAME_FIELD = "function_name";
    public static final String STATE_FIELD = "state";
    public static final String INPUT_TYPE_FIELD = "input_type";
    public static final String PROGRESS_FIELD = "progress";
    public static final String OUTPUT_INDEX_FIELD = "output_index";
    public static final String WORKER_NODE_FIELD = "worker_node";
    public static final String CREATE_TIME_FIELD = "create_time";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";
    public static final String ERROR_FIELD = "error";
    public static final String USER_FIELD = "user";
    public static final String IS_ASYNC_TASK_FIELD = "is_async";

    @Setter
    private String taskId;
    @Setter
    private String modelId;
    private final MLTaskType taskType;
    private final FunctionName functionName;
    @Setter
    private MLTaskState state;
    private final MLInputDataType inputType;
    private Float progress;
    private final String outputIndex;
    private final String workerNode;
    private final Instant createTime;
    private Instant lastUpdateTime;
    @Setter
    private String error;
    private User user; // TODO: support document level access control later
    private boolean async;

    @Builder
    public MLTask(
        String taskId,
        String modelId,
        MLTaskType taskType,
        FunctionName functionName,
        MLTaskState state,
        MLInputDataType inputType,
        Float progress,
        String outputIndex,
        String workerNode,
        Instant createTime,
        Instant lastUpdateTime,
        String error,
        User user,
        boolean async
    ) {
        this.taskId = taskId;
        this.modelId = modelId;
        this.taskType = taskType;
        this.functionName = functionName;
        this.state = state;
        this.inputType = inputType;
        this.progress = progress;
        this.outputIndex = outputIndex;
        this.workerNode = workerNode;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
        this.error = error;
        this.user = user;
        this.async = async;
    }

    public MLTask(StreamInput input) throws IOException {
        this.taskId = input.readOptionalString();
        this.modelId = input.readOptionalString();
        this.taskType = input.readEnum(MLTaskType.class);
        this.functionName = input.readEnum(FunctionName.class);
        this.state = input.readEnum(MLTaskState.class);
        this.inputType = input.readEnum(MLInputDataType.class);
        this.progress = input.readOptionalFloat();
        this.outputIndex = input.readOptionalString();
        this.workerNode = input.readString();
        this.createTime = input.readInstant();
        this.lastUpdateTime = input.readInstant();
        this.error = input.readOptionalString();
        if (input.readBoolean()) {
            this.user = new User(input);
        } else {
            this.user = null;
        }
        this.async = input.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(taskId);
        out.writeOptionalString(modelId);
        out.writeEnum(taskType);
        out.writeEnum(functionName);
        out.writeEnum(state);
        out.writeEnum(inputType);
        out.writeOptionalFloat(progress);
        out.writeOptionalString(outputIndex);
        out.writeString(workerNode);
        out.writeInstant(createTime);
        out.writeInstant(lastUpdateTime);
        out.writeOptionalString(error);
        if (user != null) {
            user.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(async);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (taskId != null) {
            builder.field(TASK_ID_FIELD, taskId);
        }
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (taskType != null) {
            builder.field(TASK_TYPE_FIELD, taskType);
        }
        if (functionName != null) {
            builder.field(FUNCTION_NAME_FIELD, functionName);
        }
        if (state != null) {
            builder.field(STATE_FIELD, state);
        }
        if (inputType != null) {
            builder.field(INPUT_TYPE_FIELD, inputType);
        }
        if (progress != null) {
            builder.field(PROGRESS_FIELD, progress);
        }
        if (outputIndex != null) {
            builder.field(OUTPUT_INDEX_FIELD, outputIndex);
        }
        if (workerNode != null) {
            builder.field(WORKER_NODE_FIELD, workerNode);
        }
        if (createTime != null) {
            builder.field(CREATE_TIME_FIELD, createTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATE_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (error != null) {
            builder.field(ERROR_FIELD, error);
        }
        if (user != null) {
            builder.field(USER_FIELD, user);
        }
        builder.field(IS_ASYNC_TASK_FIELD, async);
        return builder.endObject();
    }

    public static MLTask fromStream(StreamInput in) throws IOException {
        MLTask mlTask = new MLTask(in);
        return mlTask;
    }

    public static MLTask parse(XContentParser parser) throws IOException {
        String taskId = null;
        String modelId = null;
        MLTaskType taskType = null;
        FunctionName functionName = null;
        MLTaskState state = null;
        MLInputDataType inputType = null;
        Float progress = null;
        String outputIndex = null;
        String workerNode = null;
        Instant createTime = null;
        Instant lastUpdateTime = null;
        String error = null;
        User user = null;
        boolean async = false;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TASK_ID_FIELD:
                    taskId = parser.text();
                    break;
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case TASK_TYPE_FIELD:
                    taskType = MLTaskType.valueOf(parser.text());
                    break;
                case FUNCTION_NAME_FIELD:
                    functionName = FunctionName.from(parser.text());
                    break;
                case STATE_FIELD:
                    state = MLTaskState.valueOf(parser.text());
                    break;
                case INPUT_TYPE_FIELD:
                    inputType = MLInputDataType.valueOf(parser.text());
                    break;
                case PROGRESS_FIELD:
                    progress = parser.floatValue();
                    break;
                case OUTPUT_INDEX_FIELD:
                    outputIndex = parser.text();
                    break;
                case WORKER_NODE_FIELD:
                    workerNode = parser.text();
                    break;
                case CREATE_TIME_FIELD:
                    createTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATE_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case ERROR_FIELD:
                    error = parser.text();
                    break;
                case USER_FIELD:
                    user = User.parse(parser);
                    break;
                case IS_ASYNC_TASK_FIELD:
                    async = parser.booleanValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLTask.builder()
                .taskId(taskId)
                .modelId(modelId)
                .taskType(taskType)
                .functionName(functionName)
                .state(state)
                .inputType(inputType)
                .progress(progress)
                .outputIndex(outputIndex)
                .workerNode(workerNode)
                .createTime(createTime)
                .lastUpdateTime(lastUpdateTime)
                .error(error)
                .user(user)
                .async(async)
                .build();
    }
}

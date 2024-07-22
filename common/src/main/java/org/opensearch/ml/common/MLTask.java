/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.USER;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;
import static org.opensearch.ml.common.utils.StringUtils.gson;

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
    public static final String IS_ASYNC_TASK_FIELD = "is_async";
    public static final String TRANSFORM_JOB_FIELD = "transform_job";
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_BATCH_TRANSFORM_JOB = CommonValue.VERSION_2_16_0;

    @Setter
    private String taskId;
    @Setter
    private String modelId;
    private final MLTaskType taskType;
    @Setter
    private FunctionName functionName;
    @Setter
    private MLTaskState state;
    private final MLInputDataType inputType;
    private Float progress;
    private final String outputIndex;
    @Setter
    private List<String> workerNodes;
    private final Instant createTime;
    private Instant lastUpdateTime;
    @Setter
    private String error;
    private User user; // TODO: support document level access control later
    private boolean async;
    @Setter
    private Map<String, Object> transformJob;

    @Builder(toBuilder = true)
    public MLTask(
        String taskId,
        String modelId,
        MLTaskType taskType,
        FunctionName functionName,
        MLTaskState state,
        MLInputDataType inputType,
        Float progress,
        String outputIndex,
        List<String> workerNodes,
        Instant createTime,
        Instant lastUpdateTime,
        String error,
        User user,
        boolean async,
        Map<String, Object> transformJob
    ) {
        this.taskId = taskId;
        this.modelId = modelId;
        this.taskType = taskType;
        this.functionName = functionName;
        this.state = state;
        this.inputType = inputType;
        this.progress = progress;
        this.outputIndex = outputIndex;
        this.workerNodes = workerNodes;
        this.createTime = createTime;
        this.lastUpdateTime = lastUpdateTime;
        this.error = error;
        this.user = user;
        this.async = async;
        this.transformJob = transformJob;
    }

    public MLTask(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        this.taskId = input.readOptionalString();
        this.modelId = input.readOptionalString();
        this.taskType = input.readEnum(MLTaskType.class);
        this.functionName = input.readEnum(FunctionName.class);
        this.state = input.readEnum(MLTaskState.class);
        if (input.readBoolean()) {
            this.inputType = input.readEnum(MLInputDataType.class);
        } else {
            this.inputType = null;
        }
        this.progress = input.readOptionalFloat();
        this.outputIndex = input.readOptionalString();
        this.workerNodes = input.readStringList();
        this.createTime = input.readInstant();
        this.lastUpdateTime = input.readInstant();
        this.error = input.readOptionalString();
        if (input.readBoolean()) {
            this.user = new User(input);
        } else {
            this.user = null;
        }
        this.async = input.readBoolean();
        if (streamInputVersion.onOrAfter(MLTask.MINIMAL_SUPPORTED_VERSION_FOR_BATCH_TRANSFORM_JOB)) {
            if (input.readBoolean()) {
                String mapStr = input.readString();
                this.transformJob = gson.fromJson(mapStr, Map.class);
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalString(taskId);
        out.writeOptionalString(modelId);
        out.writeEnum(taskType);
        out.writeEnum(functionName);
        out.writeEnum(state);
        if (inputType != null) {
            out.writeBoolean(true);
            out.writeEnum(inputType);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalFloat(progress);
        out.writeOptionalString(outputIndex);
        out.writeStringCollection(workerNodes);
        out.writeInstant(createTime);
        out.writeInstant(lastUpdateTime);
        out.writeOptionalString(error);
        if (user != null) {
            user.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(async);
        if (streamOutputVersion.onOrAfter(MLTask.MINIMAL_SUPPORTED_VERSION_FOR_BATCH_TRANSFORM_JOB)) {
            if (transformJob != null) {
                out.writeBoolean(true);
                try {
                    AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                        out.writeString(gson.toJson(transformJob));
                        return null;
                    });
                } catch (PrivilegedActionException e) {
                    throw new RuntimeException(e);
                }
            } else {
                out.writeBoolean(false);
            }
        }
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
        if (workerNodes != null) {
            builder.field(WORKER_NODE_FIELD, workerNodes);
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
            builder.field(USER, user);
        }
        builder.field(IS_ASYNC_TASK_FIELD, async);
        if (transformJob != null) {
            builder.field(TRANSFORM_JOB_FIELD, transformJob);
        }
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
        List<String> workerNodes = null;
        Instant createTime = null;
        Instant lastUpdateTime = null;
        String error = null;
        User user = null;
        boolean async = false;
        Map<String, Object> transformJob = null;

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
                    if (XContentParser.Token.START_ARRAY == parser.currentToken()) {
                        workerNodes = new ArrayList<>();
                        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            workerNodes.add(parser.text());
                        }
                    } else {
                        String[] nodes = parser.text().split(",");
                        workerNodes = Arrays.asList(nodes);
                    }
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
                case USER:
                    user = User.parse(parser);
                    break;
                case IS_ASYNC_TASK_FIELD:
                    async = parser.booleanValue();
                    break;
                case TRANSFORM_JOB_FIELD:
                    transformJob = parser.map();
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
                .workerNodes(workerNodes)
                .createTime(createTime)
                .lastUpdateTime(lastUpdateTime)
                .error(error)
                .user(user)
                .async(async)
                .transformJob(transformJob)
                .build();
    }
}

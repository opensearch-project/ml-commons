/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.forward;

import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.input.MLInput;

import java.io.IOException;
import java.util.Locale;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * ML input data: algirithm name, parameters and input data set.
 */
@Data
@Log4j2
public class MLForwardInput implements ToXContentObject, Writeable {

    public static final String ALGORITHM_FIELD = "algorithm";
    public static final String NAME_FIELD = "name";
    public static final String VERSION_FIELD = "version";
    public static final String TASK_ID_FIELD = "task_id";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String WORKER_NODE_ID_FIELD = "worker_node_id";
    public static final String REQUEST_TYPE_FIELD = "request_type";
    public static final String ML_TASK_FIELD = "ml_task";
    public static final String URL_FIELD = "url";

    private FunctionName algorithm = FunctionName.CUSTOM;

    private String name;
    private Integer version;
    private String taskId;
    private String modelId;
    private String workerNodeId;
    private MLForwardRequestType requestType;
    private MLTask mlTask;
    private String url;
    MLInput modelInput;

    @Builder(toBuilder = true)
    public MLForwardInput(String name, Integer version, String taskId,String modelId, String workerNodeId, MLForwardRequestType requestType, MLTask mlTask, String url, MLInput modelInput) {
        this.name = name;
        this.version = version;
        this.taskId = taskId;
        this.modelId = modelId;
        this.workerNodeId = workerNodeId;
        this.requestType = requestType;
        this.mlTask = mlTask;
        this.url = url;
        this.modelInput = modelInput;
    }

    public MLForwardInput(StreamInput in) throws IOException {
        this.name = in.readOptionalString();
        this.version = in.readOptionalInt();
        this.algorithm = in.readEnum(FunctionName.class);
        this.taskId = in.readOptionalString();
        this.modelId = in.readOptionalString();
        this.workerNodeId = in.readOptionalString();
        this.requestType = in.readEnum(MLForwardRequestType.class);
        this.url = in.readOptionalString();
        if (in.readBoolean()) {
            mlTask = new MLTask(in);
        }
        if (in.readBoolean()) {
            this.modelInput = new MLInput(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        out.writeOptionalInt(version);
        out.writeEnum(algorithm);
        out.writeOptionalString(taskId);
        out.writeOptionalString(modelId);
        out.writeOptionalString(workerNodeId);
        out.writeEnum(requestType);
        out.writeOptionalString(url);
        if (this.mlTask != null) {
            out.writeBoolean(true);
            mlTask.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (modelInput != null) {
            out.writeBoolean(true);
            modelInput.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ALGORITHM_FIELD, algorithm.name());
        builder.field(NAME_FIELD, name);
        builder.field(VERSION_FIELD, version);
        builder.field(TASK_ID_FIELD, taskId);
        builder.field(MODEL_ID_FIELD, modelId);
        builder.field(WORKER_NODE_ID_FIELD, workerNodeId);
        builder.field(REQUEST_TYPE_FIELD, requestType);
        if (mlTask != null) {
            mlTask.toXContent(builder, params);
        }
        if (url != null) {
            builder.field(URL_FIELD, url);
        }
        //TODO: add modelInput
        builder.endObject();
        return builder;
    }

    public static MLForwardInput parse(XContentParser parser) throws IOException {
        String algorithmName = null;
        String name = null;
        Integer version = null;
        String taskId = null;
        String modelId = null;
        String workerNodeId = null;
        MLForwardRequestType requestType = null;
        MLTask mlTask = null;
        String url = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ALGORITHM_FIELD:
                    algorithmName = parser.text().toUpperCase(Locale.ROOT);
                    break;
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.intValue();
                    break;
                case TASK_ID_FIELD:
                    taskId = parser.text();
                    break;
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case WORKER_NODE_ID_FIELD:
                    workerNodeId = parser.text();
                    break;
                case REQUEST_TYPE_FIELD:
                    requestType = MLForwardRequestType.valueOf(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case ML_TASK_FIELD:
                    mlTask = MLTask.parse(parser);
                    break;
                case URL_FIELD:
                    url = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLForwardInput(name, version, taskId, modelId, workerNodeId, requestType, mlTask, url, null);
    }


    public FunctionName getFunctionName() {
        return this.algorithm;
    }

}

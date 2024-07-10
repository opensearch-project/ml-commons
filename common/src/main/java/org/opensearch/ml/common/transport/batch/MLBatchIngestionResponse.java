package org.opensearch.ml.common.transport.batch;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class MLBatchIngestionResponse extends ActionResponse implements ToXContentObject {
    public static final String TASK_ID_FIELD = "task_id";
    public static final String STATUS_FIELD = "status";

    private String taskId;
    private String status;

    public MLBatchIngestionResponse(StreamInput in) throws IOException {
        super(in);
        this.taskId = in.readString();
        this.status = in.readString();
    }

    public MLBatchIngestionResponse(String taskId, String status) {
        this.taskId = taskId;
        this.status= status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(taskId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(TASK_ID_FIELD, taskId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }

    public static MLBatchIngestionResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLBatchIngestionResponse) {
            return (MLBatchIngestionResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLBatchIngestionResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLBatchIngestionResponse", e);
        }
    }
}

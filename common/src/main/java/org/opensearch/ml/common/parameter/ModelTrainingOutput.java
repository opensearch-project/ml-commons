package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;

@Getter
public class ModelTrainingOutput extends MLOutput{
    private static final MLOutputType OUTPUT_TYPE = MLOutputType.TRAINING;
    private String modelId;
    private String status;

    @Builder
    public ModelTrainingOutput(String modelId, String status) {
        super(OUTPUT_TYPE);
        this.modelId = modelId;
        this.status= status;
    }

    public ModelTrainingOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        this.modelId = in.readOptionalString();
        this.status = in.readOptionalString();
    }

    @Override
    public MLOutputType getType() {
        return OUTPUT_TYPE;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(modelId);
        out.writeOptionalString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("model_id", modelId);
        builder.field("status", status);
        builder.endObject();
        return builder;
    }
}

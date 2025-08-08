package org.opensearch.ml.common.transport.indexInsight;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;

public class MLIndexInsightContainerDeleteResponse extends ActionResponse implements ToXContentObject {
    @Builder
    public MLIndexInsightContainerDeleteResponse() {}

    public MLIndexInsightContainerDeleteResponse(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeString("true");
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        xContentBuilder.field("acknowledge", "true");
        xContentBuilder.endObject();
        return null;
    }
}

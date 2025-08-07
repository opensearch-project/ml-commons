package org.opensearch.ml.common.transport.indexInsight;

import lombok.Builder;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

public class MLIndexInsightContainerPutResponse extends ActionResponse implements ToXContentObject {
    @Builder
    public MLIndexInsightContainerPutResponse() {
    }

    public MLIndexInsightContainerPutResponse(StreamInput streamInput) {
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

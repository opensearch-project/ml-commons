package org.opensearch.ml.common.transport.indexInsight;

import lombok.Builder;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.transport.connector.MLConnectorGetResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class MLIndexInsightGetResponse extends ActionResponse implements ToXContentObject {
    private IndexInsight indexInsight;

    @Builder
    public MLIndexInsightGetResponse(IndexInsight indexInsight) {
        this.indexInsight = indexInsight;
    }

    public MLIndexInsightGetResponse(StreamInput in) throws IOException {
        super(in);
        this.indexInsight = IndexInsight.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        indexInsight.writeTo(streamOutput);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return null;
    }

    public static MLIndexInsightGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLIndexInsightGetResponse) {
            return (MLIndexInsightGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLIndexInsightGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLIndexInsightGetResponse", e);
        }
    }
}

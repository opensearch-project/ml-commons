package org.opensearch.ml.common.transport.indexInsight;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.indexInsight.IndexInsight;

import lombok.Builder;

public class MLIndexInsightGetResponse extends ActionResponse implements ToXContentObject {
    private List<IndexInsight> indexInsights;

    @Builder
    public MLIndexInsightGetResponse(List<IndexInsight> indexInsights) {
        this.indexInsights = indexInsights;
    }

    public MLIndexInsightGetResponse(StreamInput in) throws IOException {
        super(in);
        int size = in.readVInt();
        List<IndexInsight> insights = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            insights.add(IndexInsight.fromStream(in));
        }
        this.indexInsights = insights;
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeVInt(indexInsights.size());
        for (IndexInsight insight : indexInsights) {
            insight.writeTo(streamOutput);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        if (indexInsights != null && !indexInsights.isEmpty()) {
            xContentBuilder.startArray("index_insights");
            for (IndexInsight insight : indexInsights) {
                insight.toXContent(xContentBuilder, params);
            }
            xContentBuilder.endArray();
        }
        xContentBuilder.endObject();
        return xContentBuilder;
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

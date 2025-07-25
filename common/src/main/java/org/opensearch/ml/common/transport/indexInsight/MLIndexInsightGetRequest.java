package org.opensearch.ml.common.transport.indexInsight;

import lombok.Getter;
import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
public class MLIndexInsightGetRequest extends ActionRequest {
    String indexName;

    public MLIndexInsightGetRequest(String indexName) {
        this.indexName = indexName;
    }

    public MLIndexInsightGetRequest(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.indexName = in.readString();
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    public static MLIndexInsightGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLIndexInsightGetRequest) {
            return (MLIndexInsightGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLIndexInsightGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLPredictionTaskRequest", e);
        }

    }
}

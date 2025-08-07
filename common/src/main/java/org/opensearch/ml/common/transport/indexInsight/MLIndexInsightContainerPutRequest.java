package org.opensearch.ml.common.transport.indexInsight;

import lombok.Getter;
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
public class MLIndexInsightContainerPutRequest extends ActionRequest {
    private String indexName;
    private String tenantId;

    public MLIndexInsightContainerPutRequest(String indexName, String tenantId){
        this.indexName = indexName;
        this.tenantId = tenantId;
    }

    public MLIndexInsightContainerPutRequest(StreamInput in) throws IOException {
        super(in);
        this.indexName = in.readString();
        this.tenantId = in.readString();
    }

    public static MLIndexInsightContainerPutRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLIndexInsightContainerPutRequest) {
            return (MLIndexInsightContainerPutRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLIndexInsightContainerPutRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLIndexInsightContainerPutRequest", e);
        }

    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}

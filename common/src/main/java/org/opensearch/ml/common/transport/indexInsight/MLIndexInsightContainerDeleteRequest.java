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
public class MLIndexInsightContainerDeleteRequest  extends ActionRequest {
    private String tenantId;

    public MLIndexInsightContainerDeleteRequest(String tenantId){
        this.tenantId = tenantId;
    }

    public MLIndexInsightContainerDeleteRequest(StreamInput in) throws IOException {
        super(in);
        this.tenantId = in.readOptionalString();
    }

    public static MLIndexInsightContainerDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLIndexInsightContainerDeleteRequest) {
            return (MLIndexInsightContainerDeleteRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLIndexInsightContainerDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLIndexInsightContainerDeleteRequest", e);
        }

    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}

package org.opensearch.ml.common.transport.search;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
public class MLSearchActionRequest extends SearchRequest {
    SearchRequest searchRequest;
    String tenantId;

    @Builder
    public MLSearchActionRequest(SearchRequest searchRequest, String tenantId) {
        this.searchRequest = searchRequest;
        this.tenantId = tenantId;
    }

    public MLSearchActionRequest(StreamInput input) throws IOException {
        super(input);
        if (input.readBoolean()) {
            searchRequest = new SearchRequest(input);
        }
        this.tenantId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);

        if (searchRequest != null) {
            output.writeBoolean(true); // user exists
            searchRequest.writeTo(output);
        } else {
            output.writeBoolean(false); // user does not exist
        }
        output.writeOptionalString(tenantId);
    }


    public static MLSearchActionRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLSearchActionRequest) {
            return (MLSearchActionRequest)actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLSearchActionRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLSearchActionRequest", e);
        }
    }
}

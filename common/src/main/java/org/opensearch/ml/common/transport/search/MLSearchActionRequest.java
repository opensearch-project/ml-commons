package org.opensearch.ml.common.transport.search;

import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Builder;
import lombok.Getter;

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
        Version streamInputVersion = input.getVersion();
        if (input.readBoolean()) {
            searchRequest = new SearchRequest(input);
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        Version streamOutputVersion = output.getVersion();
        if (searchRequest != null) {
            output.writeBoolean(true); // user exists
            searchRequest.writeTo(output);
        } else {
            output.writeBoolean(false); // user does not exist
        }
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            output.writeOptionalString(tenantId);
        }
    }

    public static MLSearchActionRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLSearchActionRequest) {
            return (MLSearchActionRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLSearchActionRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLSearchActionRequest", e);
        }
    }
}

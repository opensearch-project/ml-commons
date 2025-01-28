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

/**
 * Represents an extended search action request that includes a tenant ID.
 * This class allows OpenSearch to include a tenant ID in search requests,
 * which is not natively supported in the standard {@link SearchRequest}.
 */
@Getter
public class MLSearchActionRequest extends SearchRequest {
    SearchRequest searchRequest;
    String tenantId;

    /**
     * Constructor for building an MLSearchActionRequest.
     *
     * @param searchRequest The original {@link SearchRequest} to be wrapped.
     * @param tenantId The tenant ID associated with the request.
     */
    @Builder
    public MLSearchActionRequest(SearchRequest searchRequest, String tenantId) {
        this.searchRequest = searchRequest;
        this.tenantId = tenantId;
    }

    /**
     * Deserializes an {@link MLSearchActionRequest} from a {@link StreamInput}.
     *
     * @param input The stream input to read from.
     * @throws IOException If an I/O error occurs during deserialization.
     */
    public MLSearchActionRequest(StreamInput input) throws IOException {
        super(input);
        Version streamInputVersion = input.getVersion();
        if (input.readBoolean()) {
            searchRequest = new SearchRequest(input);
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
    }

    /**
     * Serializes this {@link MLSearchActionRequest} to a {@link StreamOutput}.
     *
     * @param output The stream output to write to.
     * @throws IOException If an I/O error occurs during serialization.
     */
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

    /**
     * Converts a generic {@link ActionRequest} into an {@link MLSearchActionRequest}.
     * This is useful when handling requests that may need to be converted for compatibility.
     *
     * @param actionRequest The original {@link ActionRequest}.
     * @return The converted {@link MLSearchActionRequest}.
     * @throws UncheckedIOException If the conversion fails due to an I/O error.
     */
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

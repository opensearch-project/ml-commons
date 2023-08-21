/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
public class MLUpdateConnectorRequest extends ActionRequest {
    String connectorId;
    Map<String, Object> updateContent;

    @Builder
    public MLUpdateConnectorRequest(String connectorId, Map<String, Object> updateContent) {
        this.connectorId = connectorId;
        this.updateContent = updateContent;
    }

    public MLUpdateConnectorRequest(StreamInput in) throws IOException {
        super(in);
        this.connectorId = in.readString();
        this.updateContent = in.readMap();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.connectorId);
        out.writeMap(this.getUpdateContent());
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.connectorId == null) {
            exception = addValidationError("ML connector id can't be null", exception);
        }

        return exception;
    }

    public static MLUpdateConnectorRequest parse(XContentParser parser, String connectorId) throws IOException {
        Map<String, Object> dataAsMap = null;
        dataAsMap = parser.map();

        return MLUpdateConnectorRequest.builder().connectorId(connectorId).updateContent(dataAsMap).build();
    }

    public static MLUpdateConnectorRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateConnectorRequest) {
            return (MLUpdateConnectorRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateConnectorRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLUpdateConnectorRequest", e);
        }
    }
}

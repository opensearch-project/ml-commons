/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLUpdateConnectorRequest extends ActionRequest {
    String connectorId;
    MLCreateConnectorInput updateContent;

    @Builder
    public MLUpdateConnectorRequest(String connectorId, MLCreateConnectorInput updateContent) {
        this.connectorId = connectorId;
        this.updateContent = updateContent;
    }

    public MLUpdateConnectorRequest(StreamInput in) throws IOException {
        super(in);
        this.connectorId = in.readString();
        this.updateContent = new MLCreateConnectorInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.connectorId);
        this.updateContent.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.connectorId == null) {
            exception = addValidationError("ML connector id can't be null", exception);
        }

        if (updateContent == null) {
            exception = addValidationError("Update connector content can't be null", exception);
        }

        return exception;
    }

    public static MLUpdateConnectorRequest parse(XContentParser parser, String connectorId) throws IOException {
        MLCreateConnectorInput updateContent = MLCreateConnectorInput.parse(parser, true);

        return MLUpdateConnectorRequest.builder().connectorId(connectorId).updateContent(updateContent).build();
    }

    public static MLUpdateConnectorRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateConnectorRequest) {
            return (MLUpdateConnectorRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateConnectorRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLUpdateConnectorRequest", e);
        }
    }
}

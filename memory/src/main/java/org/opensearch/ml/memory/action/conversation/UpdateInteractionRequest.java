/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

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
public class UpdateInteractionRequest extends ActionRequest {
    String interactionId;
    Map<String, Object> updateContent;

    @Builder
    public UpdateInteractionRequest(String interactionId, Map<String, Object> updateContent) {
        this.interactionId = interactionId;
        this.updateContent = updateContent;
    }

    public UpdateInteractionRequest(StreamInput in) throws IOException {
        super(in);
        this.interactionId = in.readString();
        this.updateContent = in.readMap();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.interactionId);
        out.writeMap(this.getUpdateContent());
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.interactionId == null) {
            exception = addValidationError("interaction id can't be null", exception);
        }

        return exception;
    }

    public static UpdateInteractionRequest parse(XContentParser parser, String interactionId) throws IOException {
        Map<String, Object> dataAsMap = null;
        dataAsMap = parser.map();

        return UpdateInteractionRequest.builder().interactionId(interactionId).updateContent(dataAsMap).build();
    }

    public static UpdateInteractionRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof UpdateInteractionRequest) {
            return (UpdateInteractionRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new UpdateInteractionRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into UpdateInteractionRequest", e);
        }
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private String interactionId;
    private Map<String, Object> updateContent;

    private static final Set<String> allowedList = new HashSet<>(Arrays.asList(INTERACTIONS_ADDITIONAL_INFO_FIELD));

    @Builder
    public UpdateInteractionRequest(String interactionId, Map<String, Object> updateContent) {
        this.interactionId = interactionId;
        this.updateContent = filterMapContent(updateContent);
    }

    public UpdateInteractionRequest(StreamInput in) throws IOException {
        super(in);
        this.interactionId = in.readString();
        this.updateContent = filterMapContent(in.readMap());
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

    private Map<String, Object> filterMapContent(Map<String, Object> mapContent) {
        return mapContent
            .entrySet()
            .stream()
            .filter(map -> allowedList.contains(map.getKey()))
            .collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));
    }
}

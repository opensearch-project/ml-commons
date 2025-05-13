/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Builder;
import lombok.Getter;

/**
 * MLPromptGetRequest is the request class for MLPromptGetAction.
 * It contains prompt id and tenant id that is required retrieve a prompt
 */
@Getter
public class MLPromptGetRequest extends ActionRequest {
    String promptId;
    String tenantId;

    /**
     * Construct to pass values to the MLGetPromptRequest constructor
     *
     * @param promptId a prompt id of a prompt that needs to be retrieved
     * @param tenantId a tenant id of a prompt that needs to be retrieved
     */
    @Builder
    public MLPromptGetRequest(String promptId, String tenantId) {
        this.promptId = promptId;
        this.tenantId = tenantId;
    }

    /**
     * Constructor to parse StreamInput to MLPromptGetRequest
     *
     * @param in StreamInput
     * @throws IOException if an I/O exception occurred while reading from the StreamInput
     */
    public MLPromptGetRequest(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.promptId = in.readString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
    }

    /**
     * Write MLPromptGetRequest to StreamOutput
     *
     * @param out Stream Output
     * @throws IOException if an I/O exception occurred while writing to StreamOutput
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        Version streamOutputVersion = out.getVersion();
        out.writeString(this.promptId);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(this.tenantId);
        }
    }

    /**
     * Validate MLPromptGetRequest
     *
     * @return ActionRequestValidationException if validation fails, null otherwise
     */
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.promptId == null) {
            exception = addValidationError("ML prompt id can't be null", exception);
        }

        return exception;
    }

    /**
     * Parse ActionRequest into MLPromptGetRequest
     *
     * @param actionRequest ActionRequest
     * @return MLPromptGetRequest
     */
    public static MLPromptGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLPromptGetRequest) {
            return (MLPromptGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLPromptGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLPromptGetRequest", e);
        }
    }
}

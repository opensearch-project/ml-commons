/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.config;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
public class MLConfigGetRequest extends ActionRequest {

    String configId;
    String tenantId;

    @Builder
    public MLConfigGetRequest(String configId, String tenantId) {
        this.configId = configId;
        this.tenantId = tenantId;
    }

    public MLConfigGetRequest(StreamInput in) throws IOException {
        super(in);
        this.configId = in.readString();
        // check BWC later
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.configId);
        // check BWC later
        out.writeOptionalString(this.tenantId);

    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.configId == null) {
            exception = addValidationError("ML config id can't be null", exception);
        }

        return exception;
    }

    public static MLConfigGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLConfigGetRequest) {
            return (MLConfigGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLConfigGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLConfigGetRequest", e);
        }
    }
}

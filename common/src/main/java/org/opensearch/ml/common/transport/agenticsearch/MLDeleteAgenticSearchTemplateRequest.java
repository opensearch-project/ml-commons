/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLDeleteAgenticSearchTemplateRequest extends ActionRequest {

    String templateId;

    @Builder
    public MLDeleteAgenticSearchTemplateRequest(String templateId) {
        this.templateId = templateId;
    }

    public MLDeleteAgenticSearchTemplateRequest(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (templateId == null || templateId.trim().isEmpty()) {
            exception = addValidationError("Template id cannot be null or empty", exception);
        }
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(templateId);
    }

    public static MLDeleteAgenticSearchTemplateRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLDeleteAgenticSearchTemplateRequest) {
            return (MLDeleteAgenticSearchTemplateRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLDeleteAgenticSearchTemplateRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLDeleteAgenticSearchTemplateRequest", e);
        }
    }
}

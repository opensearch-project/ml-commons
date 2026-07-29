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

/**
 * Register a search template for filling. Per the design (§4.5) the customer sends
 * only the link — the {@code _scripts} template name, the target index, and a
 * description — and registration derives the param-schema. So this request carries
 * no schema; it is produced server-side from the template body + index mapping.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLRegisterAgenticSearchTemplateRequest extends ActionRequest {

    String templateId;
    String index;
    String description;

    @Builder
    public MLRegisterAgenticSearchTemplateRequest(String templateId, String index, String description) {
        this.templateId = templateId;
        this.index = index;
        this.description = description;
    }

    public MLRegisterAgenticSearchTemplateRequest(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
        this.index = in.readString();
        this.description = in.readOptionalString();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (templateId == null || templateId.trim().isEmpty()) {
            exception = addValidationError("Template name cannot be null or empty", exception);
        }
        if (index == null || index.trim().isEmpty()) {
            exception = addValidationError("Index cannot be null or empty", exception);
        }
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(templateId);
        out.writeString(index);
        out.writeOptionalString(description);
    }

    public static MLRegisterAgenticSearchTemplateRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLRegisterAgenticSearchTemplateRequest) {
            return (MLRegisterAgenticSearchTemplateRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterAgenticSearchTemplateRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLRegisterAgenticSearchTemplateRequest", e);
        }
    }
}

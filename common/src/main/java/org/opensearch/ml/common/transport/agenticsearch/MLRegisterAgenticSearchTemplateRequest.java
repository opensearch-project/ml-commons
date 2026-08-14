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
import java.util.Map;

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
 * Register a search template for filling. The request carries the {@code templateId}
 * (the existing {@code _scripts} template id), the target index, and an optional
 * description. Registration derives the param-schema server-side from the template body
 * and index mapping. A caller may instead supply {@code paramSchema} directly; when set
 * it is validated and pre-flight rendered against the template body, then stored without
 * derivation. The {@code templateId} is also the system-index doc id and the id used by
 * get, update, and delete.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLRegisterAgenticSearchTemplateRequest extends ActionRequest {

    String templateId;
    String index;
    String description;
    /** Caller-supplied param-schema. When null the server derives it from the body and mapping. */
    Map<String, Object> paramSchema;

    @Builder
    public MLRegisterAgenticSearchTemplateRequest(String templateId, String index, String description, Map<String, Object> paramSchema) {
        this.templateId = templateId;
        this.index = index;
        this.description = description;
        this.paramSchema = paramSchema;
    }

    public MLRegisterAgenticSearchTemplateRequest(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
        this.index = in.readString();
        this.description = in.readOptionalString();
        this.paramSchema = in.readBoolean() ? in.readMap() : null;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (templateId == null || templateId.trim().isEmpty()) {
            exception = addValidationError("Template id cannot be null or empty", exception);
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
        if (paramSchema != null) {
            out.writeBoolean(true);
            out.writeMap(paramSchema);
        } else {
            out.writeBoolean(false);
        }
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

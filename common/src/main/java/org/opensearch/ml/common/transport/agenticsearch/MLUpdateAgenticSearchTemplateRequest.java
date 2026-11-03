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
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Edit a registered template's schema (§4.5): the customer PATCHes the fields the
 * two automatic inputs can't supply (descriptions, tightened enums). The {@code
 * update} carries the partial {@link AgenticSearchTemplate} to merge; whatever it
 * leaves untouched still comes from registration.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLUpdateAgenticSearchTemplateRequest extends ActionRequest {

    String templateId;
    AgenticSearchTemplate template;

    @Builder
    public MLUpdateAgenticSearchTemplateRequest(String templateId, AgenticSearchTemplate template) {
        this.templateId = templateId;
        this.template = template;
    }

    public MLUpdateAgenticSearchTemplateRequest(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
        this.template = new AgenticSearchTemplate(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (templateId == null || templateId.trim().isEmpty()) {
            exception = addValidationError("Template id cannot be null or empty", exception);
        }
        if (template == null) {
            exception = addValidationError("Update body cannot be null", exception);
        }
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(templateId);
        template.writeTo(out);
    }

    public static MLUpdateAgenticSearchTemplateRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateAgenticSearchTemplateRequest) {
            return (MLUpdateAgenticSearchTemplateRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateAgenticSearchTemplateRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLUpdateAgenticSearchTemplateRequest", e);
        }
    }
}

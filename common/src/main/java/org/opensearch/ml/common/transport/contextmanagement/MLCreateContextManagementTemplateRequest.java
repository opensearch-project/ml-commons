/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

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
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLCreateContextManagementTemplateRequest extends ActionRequest {

    String templateName;
    ContextManagementTemplate template;

    @Builder
    public MLCreateContextManagementTemplateRequest(String templateName, ContextManagementTemplate template) {
        this.templateName = templateName;
        this.template = template;
    }

    public MLCreateContextManagementTemplateRequest(StreamInput in) throws IOException {
        super(in);
        this.templateName = in.readString();
        this.template = new ContextManagementTemplate(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (templateName == null || templateName.trim().isEmpty()) {
            exception = addValidationError("Template name cannot be null or empty", exception);
        }
        if (template == null) {
            exception = addValidationError("Context management template cannot be null", exception);
        } else {
            // Validate template structure
            if (template.getName() == null || template.getName().trim().isEmpty()) {
                exception = addValidationError("Template name in body cannot be null or empty", exception);
            }
            if (template.getHooks() == null || template.getHooks().isEmpty()) {
                exception = addValidationError("Template must define at least one hook", exception);
            }
        }
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(templateName);
        template.writeTo(out);
    }

    public static MLCreateContextManagementTemplateRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLCreateContextManagementTemplateRequest) {
            return (MLCreateContextManagementTemplateRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreateContextManagementTemplateRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreateContextManagementTemplateRequest", e);
        }
    }
}

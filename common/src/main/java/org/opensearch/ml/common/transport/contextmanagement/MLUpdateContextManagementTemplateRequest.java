/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
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
public class MLUpdateContextManagementTemplateRequest extends ActionRequest {

    String templateName;
    ContextManagementTemplate template;

    @Builder
    public MLUpdateContextManagementTemplateRequest(String templateName, ContextManagementTemplate template) {
        this.templateName = templateName;
        this.template = template;
    }

    public MLUpdateContextManagementTemplateRequest(StreamInput in) throws IOException {
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
        }
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(templateName);
        template.writeTo(out);
    }
}

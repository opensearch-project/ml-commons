/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.utils.StringUtils.validateFields;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.utils.FieldDescriptor;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLUpdateModelRequest extends ActionRequest {

    MLUpdateModelInput updateModelInput;

    @Builder
    public MLUpdateModelRequest(MLUpdateModelInput updateModelInput) {
        this.updateModelInput = updateModelInput;
    }

    public MLUpdateModelRequest(StreamInput in) throws IOException {
        super(in);
        updateModelInput = new MLUpdateModelInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (updateModelInput == null) {
            return addValidationError("Update Model Input can't be null", null);
        }
        Map<String, FieldDescriptor> fieldsToValidate = new HashMap<>();
        fieldsToValidate.put("Model Name", new FieldDescriptor(updateModelInput.getName(), false));
        fieldsToValidate.put("Model Description", new FieldDescriptor(updateModelInput.getDescription(), false));
        return validateFields(fieldsToValidate);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.updateModelInput.writeTo(out);
    }

    public static MLUpdateModelRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateModelRequest) {
            return (MLUpdateModelRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput in = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateModelRequest(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLUpdateModelRequest", e);
        }
    }
}

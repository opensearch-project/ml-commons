/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.register;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.utils.Validator.validateFields;

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
public class MLRegisterModelRequest extends ActionRequest {

    MLRegisterModelInput registerModelInput;

    @Builder
    public MLRegisterModelRequest(MLRegisterModelInput registerModelInput) {
        this.registerModelInput = registerModelInput;
    }

    public MLRegisterModelRequest(StreamInput in) throws IOException {
        super(in);
        this.registerModelInput = new MLRegisterModelInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (registerModelInput == null) {
            return addValidationError("ML input can't be null", null);
        }

        Map<String, FieldDescriptor> fieldsToValidate = new HashMap<>();
        fieldsToValidate.put("Model name", new FieldDescriptor(registerModelInput.getModelName(), true));
        fieldsToValidate.put("Model description", new FieldDescriptor(registerModelInput.getDescription(), false));

        return validateFields(fieldsToValidate);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.registerModelInput.writeTo(out);
    }

    public static MLRegisterModelRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLRegisterModelRequest) {
            return (MLRegisterModelRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterModelRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLRegisterModelRequest", e);
        }

    }
}

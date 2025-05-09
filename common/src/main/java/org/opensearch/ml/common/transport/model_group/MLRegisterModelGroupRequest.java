/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLRegisterModelGroupRequest extends ActionRequest {

    MLRegisterModelGroupInput registerModelGroupInput;

    @Builder
    public MLRegisterModelGroupRequest(MLRegisterModelGroupInput registerModelGroupInput) {
        this.registerModelGroupInput = registerModelGroupInput;
    }

    public MLRegisterModelGroupRequest(StreamInput in) throws IOException {
        super(in);
        this.registerModelGroupInput = new MLRegisterModelGroupInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (registerModelGroupInput == null) {
            return addValidationError("Model group input can't be null", null);
        }

        Map<String, String> fieldsToValidate = new HashMap<>();
        fieldsToValidate.put("Model group name", registerModelGroupInput.getName());
        fieldsToValidate.put("Model group description", registerModelGroupInput.getDescription());

        return validateFields(fieldsToValidate);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.registerModelGroupInput.writeTo(out);
    }

    public static MLRegisterModelGroupRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLRegisterModelGroupRequest) {
            return (MLRegisterModelGroupRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterModelGroupRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLRegisterModelGroupRequest", e);
        }

    }
}

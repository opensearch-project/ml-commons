/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

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

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLCreateConnectorRequest extends ActionRequest {
    private MLCreateConnectorInput mlCreateConnectorInput;

    @Builder
    public MLCreateConnectorRequest(MLCreateConnectorInput mlCreateConnectorInput) {
        this.mlCreateConnectorInput = mlCreateConnectorInput;
    }

    public MLCreateConnectorRequest(StreamInput in) throws IOException {
        super(in);
        this.mlCreateConnectorInput = new MLCreateConnectorInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (mlCreateConnectorInput == null) {
            return addValidationError("ML Connector input can't be null", null);
        }
        Map<String, FieldDescriptor> fieldsToValidate = new HashMap<>();
        fieldsToValidate.put("Model connector name", new FieldDescriptor(mlCreateConnectorInput.getName(), true));
        fieldsToValidate.put("Model connector description", new FieldDescriptor(mlCreateConnectorInput.getDescription(), false));

        return validateFields(fieldsToValidate);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlCreateConnectorInput.writeTo(out);
    }

    public static MLCreateConnectorRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLCreateConnectorRequest) {
            return (MLCreateConnectorRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreateConnectorRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreateConnectorRequest", e);
        }
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE;
import static org.opensearch.ml.common.utils.StringUtils.validateFields;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.DocRequest;
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
public class MLUpdateModelGroupRequest extends ActionRequest implements DocRequest {

    MLUpdateModelGroupInput updateModelGroupInput;

    @Builder
    public MLUpdateModelGroupRequest(MLUpdateModelGroupInput updateModelGroupInput) {
        this.updateModelGroupInput = updateModelGroupInput;
    }

    public MLUpdateModelGroupRequest(StreamInput in) throws IOException {
        super(in);
        this.updateModelGroupInput = new MLUpdateModelGroupInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (updateModelGroupInput == null) {
            return addValidationError("Update Model group input can't be null", null);
        }

        Map<String, FieldDescriptor> fieldsToValidate = new HashMap<>();
        fieldsToValidate.put("Model group name", new FieldDescriptor(updateModelGroupInput.getName(), false));
        fieldsToValidate.put("Model group description", new FieldDescriptor(updateModelGroupInput.getDescription(), false));

        return validateFields(fieldsToValidate);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.updateModelGroupInput.writeTo(out);
    }

    public static MLUpdateModelGroupRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateModelGroupRequest) {
            return (MLUpdateModelGroupRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateModelGroupRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLUpdateModelGroupRequest", e);
        }

    }

    @Override
    public String type() {
        return ML_MODEL_GROUP_RESOURCE_TYPE;
    }

    /**
     * Get the index that this request operates on
     *
     * @return the index
     */
    @Override
    public String index() {
        return ML_MODEL_GROUP_INDEX;
    }

    /**
     * Get the id of the document for this request
     *
     * @return the id
     */
    @Override
    public String id() {
        return updateModelGroupInput.getModelGroupID();
    }
}

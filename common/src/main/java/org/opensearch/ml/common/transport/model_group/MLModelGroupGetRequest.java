/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.DocRequest;
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
public class MLModelGroupGetRequest extends ActionRequest implements DocRequest {

    String modelGroupId;
    String tenantId;

    @Builder
    public MLModelGroupGetRequest(String modelGroupId, String tenantId) {
        this.modelGroupId = modelGroupId;
        this.tenantId = tenantId;
    }

    public MLModelGroupGetRequest(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.modelGroupId = in.readString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        Version streamOutputVersion = out.getVersion();
        out.writeString(this.modelGroupId);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.modelGroupId == null) {
            exception = addValidationError("Model group id can't be null", exception);
        }

        return exception;
    }

    public static MLModelGroupGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLModelGroupGetRequest) {
            return (MLModelGroupGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelGroupGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLModelGroupGetRequest", e);
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
        return modelGroupId;
    }
}

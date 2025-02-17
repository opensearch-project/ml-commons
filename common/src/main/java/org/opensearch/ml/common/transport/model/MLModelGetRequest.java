/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.Version;
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
public class MLModelGetRequest extends ActionRequest {

    String modelId;
    boolean returnContent;
    // This is to identify if the get request is initiated by user or not. Sometimes during
    // delete/update options, we also perform get operation. This field is to distinguish between
    // these two situations.
    boolean isUserInitiatedGetRequest;
    String tenantId;

    @Builder
    public MLModelGetRequest(String modelId, boolean returnContent, boolean isUserInitiatedGetRequest, String tenantId) {
        this.modelId = modelId;
        this.returnContent = returnContent;
        this.isUserInitiatedGetRequest = isUserInitiatedGetRequest;
        this.tenantId = tenantId;
    }

    public MLModelGetRequest(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.modelId = in.readString();
        this.returnContent = in.readBoolean();
        this.isUserInitiatedGetRequest = in.readBoolean();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;

    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        Version streamOutputVersion = out.getVersion();
        out.writeString(this.modelId);
        out.writeBoolean(returnContent);
        out.writeBoolean(isUserInitiatedGetRequest);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.modelId == null) {
            exception = addValidationError("ML model id can't be null", exception);
        }

        return exception;
    }

    public static MLModelGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLModelGetRequest) {
            return (MLModelGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLModelGetRequest", e);
        }
    }
}

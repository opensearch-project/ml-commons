/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prediction;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskRequest;
import org.opensearch.transport.TransportChannel;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class MLPredictionTaskRequest extends MLTaskRequest {

    @Getter
    @Setter
    private transient TransportChannel streamingChannel;

    String modelId;
    MLInput mlInput;
    String tenantId;
    @Setter
    User user;

    @Builder
    public MLPredictionTaskRequest(String modelId, MLInput mlInput, boolean dispatchTask, User user, String tenantId) {
        super(dispatchTask);
        this.mlInput = mlInput;
        this.modelId = modelId;
        this.user = user;
        this.tenantId = tenantId;
    }

    public MLPredictionTaskRequest(String modelId, MLInput mlInput) {
        // TODO: this is invoked in chat agent runner. I'll refactor this when work on agents.
        this(modelId, mlInput, true, null, null);
    }

    public MLPredictionTaskRequest(String modelId, MLInput mlInput, User user, String tenantId) {
        this(modelId, mlInput, true, user, tenantId);
    }

    public MLPredictionTaskRequest(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.modelId = in.readOptionalString();
        this.mlInput = new MLInput(in);
        if (in.readBoolean()) {
            this.user = new User(in);
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalString(this.modelId);
        this.mlInput.writeTo(out);
        if (user != null) {
            out.writeBoolean(true);
            user.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (this.mlInput == null) {
            exception = addValidationError("ML input can't be null", exception);
        } else if (this.mlInput.getInputDataset() == null) {
            exception = addValidationError("input data can't be null", exception);
        }

        return exception;
    }

    public static MLPredictionTaskRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLPredictionTaskRequest) {
            return (MLPredictionTaskRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLPredictionTaskRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLPredictionTaskRequest", e);
        }
    }

}

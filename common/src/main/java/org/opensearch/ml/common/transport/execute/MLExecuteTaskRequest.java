/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.execute;

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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.transport.MLTaskRequest;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLExecuteTaskRequest extends MLTaskRequest {

    FunctionName functionName;
    Input input;

    @Builder
    public MLExecuteTaskRequest(@NonNull FunctionName functionName, Input input, boolean dispatchTask) {
        super(dispatchTask);
        this.functionName = functionName;
        this.input = input;
    }

    public MLExecuteTaskRequest(@NonNull FunctionName functionName, Input input) {
        this(functionName, input, true);
    }

    public MLExecuteTaskRequest(StreamInput in) throws IOException {
        super(in);
        this.functionName = in.readEnum(FunctionName.class);
        this.input = MLCommonsClassLoader.initExecuteInputInstance(functionName, in, StreamInput.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeEnum(functionName);
        this.input.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (this.input == null) {
            exception = addValidationError("ML input can't be null", exception);
        } else {
            if (this.input.getFunctionName() == null) {
                exception = addValidationError("function name can't be null or empty", exception);
            }
        }

        return exception;
    }

    public static MLExecuteTaskRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLExecuteTaskRequest) {
            return (MLExecuteTaskRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLExecuteTaskRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLExecuteTaskRequest", e);
        }

    }
}

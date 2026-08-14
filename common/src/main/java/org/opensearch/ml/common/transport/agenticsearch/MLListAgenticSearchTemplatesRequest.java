/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLListAgenticSearchTemplatesRequest extends ActionRequest {

    int from;
    int size;

    @Builder
    public MLListAgenticSearchTemplatesRequest(int from, int size) {
        this.from = from;
        this.size = size;
    }

    public MLListAgenticSearchTemplatesRequest(StreamInput in) throws IOException {
        super(in);
        this.from = in.readInt();
        this.size = in.readInt();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (from < 0) {
            exception = addValidationError("Parameter 'from' must be non-negative", exception);
        }
        if (size <= 0 || size > 1000) {
            exception = addValidationError("Parameter 'size' must be between 1 and 1000", exception);
        }
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeInt(from);
        out.writeInt(size);
    }

    public static MLListAgenticSearchTemplatesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLListAgenticSearchTemplatesRequest) {
            return (MLListAgenticSearchTemplatesRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLListAgenticSearchTemplatesRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLListAgenticSearchTemplatesRequest", e);
        }
    }
}

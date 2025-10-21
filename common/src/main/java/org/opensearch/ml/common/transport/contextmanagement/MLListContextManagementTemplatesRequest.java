/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

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
public class MLListContextManagementTemplatesRequest extends ActionRequest {

    int from;
    int size;

    @Builder
    public MLListContextManagementTemplatesRequest(int from, int size) {
        this.from = from;
        this.size = size;
    }

    public MLListContextManagementTemplatesRequest(StreamInput in) throws IOException {
        super(in);
        this.from = in.readInt();
        this.size = in.readInt();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        // No specific validation needed for list request
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeInt(from);
        out.writeInt(size);
    }

    public static MLListContextManagementTemplatesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLListContextManagementTemplatesRequest) {
            return (MLListContextManagementTemplatesRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLListContextManagementTemplatesRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLListContextManagementTemplatesRequest", e);
        }
    }
}

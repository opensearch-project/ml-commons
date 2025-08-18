/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memorycontainer.MLMemory;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MLGetMemoryResponse extends ActionResponse implements ToXContentObject {
    MLMemory mlMemory;

    @Builder
    public MLGetMemoryResponse(MLMemory mlMemory) {
        this.mlMemory = mlMemory;
    }

    public MLGetMemoryResponse(StreamInput in) throws IOException {
        super(in);
        mlMemory = new MLMemory(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        mlMemory.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return mlMemory.toXContent(xContentBuilder, params);
    }

    public static MLGetMemoryResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLGetMemoryResponse) {
            return (MLGetMemoryResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLGetMemoryResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLMemoryGetResponse", e);
        }
    }
}

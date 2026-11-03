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
import org.opensearch.ml.common.memorycontainer.MLWorkingMemory;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MLGetWorkingMemoryResponse extends ActionResponse implements ToXContentObject {

    private MLWorkingMemory workingMemory;

    @Builder
    public MLGetWorkingMemoryResponse(MLWorkingMemory workingMemory) {
        this.workingMemory = workingMemory;
    }

    public MLGetWorkingMemoryResponse(StreamInput in) throws IOException {
        super(in);
        workingMemory = new MLWorkingMemory(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        workingMemory.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return workingMemory.toXContent(xContentBuilder, params);
    }

    public static MLGetWorkingMemoryResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLGetWorkingMemoryResponse) {
            return (MLGetWorkingMemoryResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLGetWorkingMemoryResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLGetWorkingMemoryResponse", e);
        }
    }
}

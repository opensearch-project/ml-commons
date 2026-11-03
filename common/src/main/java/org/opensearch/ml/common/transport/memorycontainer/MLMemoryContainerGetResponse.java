/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

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
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MLMemoryContainerGetResponse extends ActionResponse implements ToXContentObject {

    MLMemoryContainer mlMemoryContainer;

    @Builder
    public MLMemoryContainerGetResponse(MLMemoryContainer mlMemoryContainer) {
        this.mlMemoryContainer = mlMemoryContainer;
    }

    public MLMemoryContainerGetResponse(StreamInput in) throws IOException {
        super(in);
        mlMemoryContainer = new MLMemoryContainer(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        mlMemoryContainer.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return mlMemoryContainer.toXContent(xContentBuilder, params);
    }

    public static MLMemoryContainerGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLMemoryContainerGetResponse) {
            return (MLMemoryContainerGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMemoryContainerGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLMemoryContainerGetResponse", e);
        }
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport;

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
import org.opensearch.ml.common.output.MLOutput;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MLTaskResponse extends ActionResponse implements ToXContentObject {

    MLOutput output;

    @Builder
    public MLTaskResponse(MLOutput output) {
        this.output = output;
    }

    public MLTaskResponse(StreamInput in) throws IOException {
        super(in);
        output = MLOutput.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        output.writeTo(out);
    }

    public static MLTaskResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLTaskResponse) {
            return (MLTaskResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLTaskResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLTaskResponse", e);
        }
    }

    @Override
    public XContentBuilder toXContent(final XContentBuilder builder, final Params params) throws IOException {
        return output.toXContent(builder, params);
    }
}

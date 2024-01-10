/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.tools;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.ToolMetadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
@ToString
public class MLToolGetResponse extends ActionResponse implements ToXContentObject {

    ToolMetadata toolMetadata;

    @Builder
    public MLToolGetResponse(ToolMetadata toolMetadata) {
        this.toolMetadata = toolMetadata;
    }

    public MLToolGetResponse(StreamInput in) throws IOException {
        super(in);
        toolMetadata = toolMetadata.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        toolMetadata.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        return toolMetadata.toXContent(builder, params);
    }

    public static MLToolGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLToolGetResponse) {
            return (MLToolGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLToolGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLToolGetResponse", e);
        }
    }
}
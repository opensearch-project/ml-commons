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
import java.util.List;

@Getter
@ToString
public class MLToolsListResponse extends ActionResponse implements ToXContentObject {

    List<ToolMetadata> toolMetadataList;

    @Builder
    public MLToolsListResponse(List<ToolMetadata> toolMetadata) {
        this.toolMetadataList = toolMetadata;
    }
    public MLToolsListResponse(StreamInput in) throws IOException {
        super(in);
        this.toolMetadataList = in.readList(ToolMetadata::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeList(toolMetadataList);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, ToXContent.Params params) throws IOException {
        xContentBuilder.startObject();
        for (ToolMetadata toolMetadata : toolMetadataList) {
            xContentBuilder.field(toolMetadata.getName(), toolMetadata.getDescription());
        }
        xContentBuilder.endObject();
        return xContentBuilder;
    }

    public static MLToolsListResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLToolsListResponse) {
            return (MLToolsListResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLToolsListResponse(input);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLToolsListResponse", e);
        }
    }
}
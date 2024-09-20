/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.ToolMetadata;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLToolsListRequest extends ActionRequest {

    List<ToolMetadata> toolMetadataList;

    @Builder
    public MLToolsListRequest(List<ToolMetadata> toolMetadataList) {
        this.toolMetadataList = toolMetadataList;
    }

    public MLToolsListRequest(StreamInput in) throws IOException {
        super(in);
        this.toolMetadataList = in.readList(ToolMetadata::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeList(this.toolMetadataList);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    public static MLToolsListRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLToolsListRequest) {
            return (MLToolsListRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLToolsListRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLToolsListRequest", e);
        }
    }

}

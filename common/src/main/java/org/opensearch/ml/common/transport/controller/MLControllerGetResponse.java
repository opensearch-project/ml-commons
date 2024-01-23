/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.controller.MLController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class MLControllerGetResponse extends ActionResponse implements ToXContentObject {

    @Getter
    MLController controller;

    @Builder
    public MLControllerGetResponse(MLController controller) {
        this.controller = controller;
    }

    public MLControllerGetResponse(StreamInput in) throws IOException {
        super(in);
        controller = new MLController(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        controller.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, ToXContent.Params params) throws IOException {
        return controller.toXContent(xContentBuilder, params);
    }

    public static MLControllerGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLControllerGetResponse) {
            return (MLControllerGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLControllerGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLControllerGetResponse", e);
        }
    }
}

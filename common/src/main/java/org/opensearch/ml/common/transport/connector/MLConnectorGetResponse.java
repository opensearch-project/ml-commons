/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.connector.Connector;

import lombok.Builder;

public class MLConnectorGetResponse extends ActionResponse implements ToXContentObject {
    Connector mlConnector;

    @Builder
    public MLConnectorGetResponse(Connector mlConnector) {
        this.mlConnector = mlConnector;
    }

    public MLConnectorGetResponse(StreamInput in) throws IOException {
        super(in);
        mlConnector = Connector.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        mlConnector.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, ToXContent.Params params) throws IOException {
        return mlConnector.toXContent(xContentBuilder, params);
    }

    public static MLConnectorGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLConnectorGetResponse) {
            return (MLConnectorGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLConnectorGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLConnectorGetResponse", e);
        }
    }

}

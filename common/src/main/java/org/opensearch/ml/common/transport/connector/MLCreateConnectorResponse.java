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
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;

@Getter
public class MLCreateConnectorResponse extends ActionResponse implements ToXContentObject {
    public static final String Connector_ID_FIELD = "connector_id";
    public static final String STATUS_FIELD = "status";

    private String connectorId;

    public MLCreateConnectorResponse(StreamInput in) throws IOException {
        super(in);
        this.connectorId = in.readString();
    }

    public MLCreateConnectorResponse(String taskId) {
        this.connectorId = taskId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(connectorId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(Connector_ID_FIELD, connectorId);
        builder.endObject();
        return builder;
    }

    public static MLCreateConnectorResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLCreateConnectorResponse) {
            return (MLCreateConnectorResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreateConnectorResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLCreateConnectorResponse", e);
        }

    }
}

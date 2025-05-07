/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.remove;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.util.CollectionUtils;

import lombok.Data;

@Data
public class MLMcpToolsRemoveNodesRequest extends BaseNodesRequest<MLMcpToolsRemoveNodesRequest> {
    private List<String> tools;

    public MLMcpToolsRemoveNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.tools = in.readList(StreamInput::readString);
    }

    public MLMcpToolsRemoveNodesRequest(String[] nodeIds, List<String> tools) {
        super(nodeIds);
        this.tools = tools;
    }

    @Override
    public ActionRequestValidationException validate() {
        if (CollectionUtils.isEmpty(tools)) {
            ActionRequestValidationException exception = new ActionRequestValidationException();
            exception.addValidationError("remove tools list can not be null");
            return exception;
        }
        return null;
    }

    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeStringArray(tools.toArray(new String[0]));
    }

    public static MLMcpToolsRemoveNodesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMcpToolsRemoveNodesRequest) {
            return (MLMcpToolsRemoveNodesRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpToolsRemoveNodesRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLMcpToolsRemoveNodesRequest", e);
        }
    }
}

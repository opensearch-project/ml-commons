package org.opensearch.ml.common.transport.mcpserver.requests.register;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;

import lombok.Data;

@Data
public class MLMcpToolsRegisterNodesRequest extends BaseNodesRequest<MLMcpToolsRegisterNodesRequest> {
    private McpTools mcpTools;

    public MLMcpToolsRegisterNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.mcpTools = new McpTools(in);
    }

    public MLMcpToolsRegisterNodesRequest(String[] nodeIds, McpTools mcpTools) {
        super(nodeIds);
        this.mcpTools = mcpTools;
    }

    @Override
    public ActionRequestValidationException validate() {
        if (CollectionUtils.isEmpty(mcpTools.getTools())) {
            ActionRequestValidationException exception = new ActionRequestValidationException();
            exception.addValidationError("tools list can not be null");
            return exception;
        }
        return null;
    }

    public static MLMcpToolsRegisterNodesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLRegisterAgentRequest) {
            return (MLMcpToolsRegisterNodesRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpToolsRegisterNodesRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLMcpToolsRegisterRequest", e);
        }
    }
}

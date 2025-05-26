package org.opensearch.ml.common.transport.mcpserver.responses.list;

import java.io.IOException;
import java.util.List;

import lombok.Getter;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;

@Getter
public class MLMcpToolsListResponse extends ActionResponse implements ToXContentObject {
    private final List<RegisterMcpTool> mcpTools;

    public MLMcpToolsListResponse(StreamInput input) throws IOException {
        super(input);
        mcpTools = input.readList(RegisterMcpTool::new);
    }

    public MLMcpToolsListResponse(List<RegisterMcpTool> mcpTools) {
        this.mcpTools = mcpTools;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        xContentBuilder.startArray(MLAgent.TOOLS_FIELD);
        for (RegisterMcpTool tool : mcpTools) {
            tool.toXContent(xContentBuilder, params);
        }
        xContentBuilder.endArray();
        xContentBuilder.endObject();
        return xContentBuilder;
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeList(mcpTools);
    }
}

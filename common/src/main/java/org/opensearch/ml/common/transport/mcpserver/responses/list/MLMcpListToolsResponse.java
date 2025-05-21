package org.opensearch.ml.common.transport.mcpserver.responses.list;

import java.io.IOException;
import java.util.List;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;

public class MLMcpListToolsResponse extends ActionResponse implements ToXContentObject {
    private final List<RegisterMcpTool> tools;

    public MLMcpListToolsResponse(StreamInput input) throws IOException {
        super(input);
        tools = input.readList(streamInput -> new RegisterMcpTool(streamInput));
    }

    public MLMcpListToolsResponse(List<RegisterMcpTool> tools) {
        this.tools = tools;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        xContentBuilder.startArray("tools");
        for (RegisterMcpTool tool : tools) {
            tool.toXContent(xContentBuilder, params);
        }
        xContentBuilder.endArray();
        xContentBuilder.endObject();
        return xContentBuilder;
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeList(tools);
    }
}

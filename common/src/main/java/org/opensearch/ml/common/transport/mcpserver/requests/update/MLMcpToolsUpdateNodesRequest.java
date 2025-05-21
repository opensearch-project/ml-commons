/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.update;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.agent.MLAgent.TOOLS_FIELD;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class MLMcpToolsUpdateNodesRequest extends BaseNodesRequest<MLMcpToolsUpdateNodesRequest> {
    private List<UpdateMcpTool> mcpTools;

    public MLMcpToolsUpdateNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.mcpTools = in.readList(UpdateMcpTool::new);
    }

    public MLMcpToolsUpdateNodesRequest(String[] nodeIds, List<UpdateMcpTool> mcpTools) {
        super(nodeIds);
        this.mcpTools = mcpTools;
    }

    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeList(mcpTools);
    }

    public static MLMcpToolsUpdateNodesRequest parse(XContentParser parser, String[] allNodeIds) throws IOException {
        List<UpdateMcpTool> mcpTools = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            if (fieldName.equals(TOOLS_FIELD)) {
                mcpTools = new ArrayList<>();
                ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    mcpTools.add(UpdateMcpTool.parse(parser));
                }
            } else {
                parser.skipChildren();
            }
        }
        return new MLMcpToolsUpdateNodesRequest(allNodeIds, mcpTools);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (CollectionUtils.isEmpty(mcpTools)) {
            ActionRequestValidationException exception = new ActionRequestValidationException();
            exception.addValidationError("tools list can not be null");
            return exception;
        }
        return null;
    }

    public static MLMcpToolsUpdateNodesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMcpToolsUpdateNodesRequest) {
            return (MLMcpToolsUpdateNodesRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpToolsUpdateNodesRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLMcpToolsUpdateRequest", e);
        }
    }
}

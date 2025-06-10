/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

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
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This class extends {@link BaseNodesRequest} and is used to register tools on nodes in the cluster.
 * It's used by {@link TransportNodesAction }  which is used to send requests to multiple nodes in the cluster,
 * the {@link BaseNodesRequest } contains both node ids and the actual requests that will be sent to the nodes.
 * The {@link MLMcpToolsRegisterNodeRequest} is the actual request that will be sent to the nodes.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MLMcpToolsRegisterNodesRequest extends BaseNodesRequest<MLMcpToolsRegisterNodesRequest> {
    private List<McpToolRegisterInput> mcpTools;

    public MLMcpToolsRegisterNodesRequest(StreamInput in) throws IOException {
        super(in);
        this.mcpTools = in.readList(McpToolRegisterInput::new);
    }

    public MLMcpToolsRegisterNodesRequest(String[] nodeIds, List<McpToolRegisterInput> mcpTools) {
        super(nodeIds);
        this.mcpTools = mcpTools;
    }

    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeList(mcpTools);
    }

    public static MLMcpToolsRegisterNodesRequest parse(XContentParser parser, String[] allNodeIds) throws IOException {
        List<McpToolRegisterInput> mcpTools = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            if (fieldName.equals(TOOLS_FIELD)) {
                mcpTools = new ArrayList<>();
                ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    mcpTools.add(McpToolRegisterInput.parse(parser));
                }
            } else {
                parser.skipChildren();
            }
        }
        return new MLMcpToolsRegisterNodesRequest(allNodeIds, mcpTools);
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

    public static MLMcpToolsRegisterNodesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMcpToolsRegisterNodesRequest) {
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

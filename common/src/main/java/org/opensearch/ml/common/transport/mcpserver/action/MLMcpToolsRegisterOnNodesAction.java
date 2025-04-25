/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.rest.mcpserver.responses.MLMcpRegisterNodesResponse;

public class MLMcpToolsRegisterOnNodesAction extends ActionType<MLMcpRegisterNodesResponse> {
    public static MLMcpToolsRegisterOnNodesAction INSTANCE = new MLMcpToolsRegisterOnNodesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp_tools/register_on_nodes";

    private MLMcpToolsRegisterOnNodesAction() {
        super(NAME, MLMcpRegisterNodesResponse::new);
    }

}

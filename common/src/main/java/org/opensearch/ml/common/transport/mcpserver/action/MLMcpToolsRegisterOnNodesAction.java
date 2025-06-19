/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodesResponse;

public class MLMcpToolsRegisterOnNodesAction extends ActionType<MLMcpToolsRegisterNodesResponse> {
    public static MLMcpToolsRegisterOnNodesAction INSTANCE = new MLMcpToolsRegisterOnNodesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp_tools/register_on_nodes";

    private MLMcpToolsRegisterOnNodesAction() {
        super(NAME, MLMcpToolsRegisterNodesResponse::new);
    }

}

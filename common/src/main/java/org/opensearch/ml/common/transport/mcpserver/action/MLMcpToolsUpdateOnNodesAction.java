/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodesResponse;

public class MLMcpToolsUpdateOnNodesAction extends ActionType<MLMcpToolsUpdateNodesResponse> {
    public static MLMcpToolsUpdateOnNodesAction INSTANCE = new MLMcpToolsUpdateOnNodesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp_tools/update_on_nodes";

    private MLMcpToolsUpdateOnNodesAction() {
        super(NAME, MLMcpToolsUpdateNodesResponse::new);
    }

}

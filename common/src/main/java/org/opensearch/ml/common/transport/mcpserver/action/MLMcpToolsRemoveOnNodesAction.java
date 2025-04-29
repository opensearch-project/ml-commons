/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpRemoveNodesResponse;

public class MLMcpToolsRemoveOnNodesAction extends ActionType<MLMcpRemoveNodesResponse> {
    public static MLMcpToolsRemoveOnNodesAction INSTANCE = new MLMcpToolsRemoveOnNodesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp_tools/remove_on_nodes";

    private MLMcpToolsRemoveOnNodesAction() {
        super(NAME, MLMcpRemoveNodesResponse::new);
    }

}

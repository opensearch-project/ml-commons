/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.remove.MLMcpRemoveNodesResponse;

public class MLMcpToolsRemoveAction extends ActionType<MLMcpRemoveNodesResponse> {
    public static MLMcpToolsRemoveAction INSTANCE = new MLMcpToolsRemoveAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp_tools/remove";

    private MLMcpToolsRemoveAction() {
        super(NAME, MLMcpRemoveNodesResponse::new);
    }

}

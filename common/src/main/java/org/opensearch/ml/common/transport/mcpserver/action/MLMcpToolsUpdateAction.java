/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.update.MLMcpToolsUpdateNodesResponse;

public class MLMcpToolsUpdateAction extends ActionType<MLMcpToolsUpdateNodesResponse> {
    public static MLMcpToolsUpdateAction INSTANCE = new MLMcpToolsUpdateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp_tools/update";

    private MLMcpToolsUpdateAction() {
        super(NAME, MLMcpToolsUpdateNodesResponse::new);
    }

}

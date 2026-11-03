/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodesResponse;

public class MLMcpToolsRegisterAction extends ActionType<MLMcpToolsRegisterNodesResponse> {
    public static MLMcpToolsRegisterAction INSTANCE = new MLMcpToolsRegisterAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp_tools/register";

    private MLMcpToolsRegisterAction() {
        super(NAME, MLMcpToolsRegisterNodesResponse::new);
    }

}

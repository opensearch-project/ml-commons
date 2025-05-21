/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpListToolsResponse;

public class MLMcpToolsListAction extends ActionType<MLMcpListToolsResponse> {
    public static MLMcpToolsListAction INSTANCE = new MLMcpToolsListAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp_tools/list";

    private MLMcpToolsListAction() {
        super(NAME, MLMcpListToolsResponse::new);
    }

}

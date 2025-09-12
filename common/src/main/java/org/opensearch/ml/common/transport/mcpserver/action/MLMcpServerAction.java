/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.server.MLMcpServerResponse;

public class MLMcpServerAction extends ActionType<MLMcpServerResponse> {
    public static MLMcpServerAction INSTANCE = new MLMcpServerAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp/server";

    private MLMcpServerAction() {
        super(NAME, MLMcpServerResponse::new);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.mcpserver.responses.list.MLMcpConnectorListToolsResponse;

public class MLMcpConnectorListToolsAction extends ActionType<MLMcpConnectorListToolsResponse> {

    public static final MLMcpConnectorListToolsAction INSTANCE = new MLMcpConnectorListToolsAction();
    public static final String NAME = "cluster:admin/opensearch/ml/connectors/tools/list";

    private MLMcpConnectorListToolsAction() {
        super(NAME, MLMcpConnectorListToolsResponse::new);
    }
}

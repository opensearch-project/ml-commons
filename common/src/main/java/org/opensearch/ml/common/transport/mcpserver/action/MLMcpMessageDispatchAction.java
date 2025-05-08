/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;

public class MLMcpMessageDispatchAction extends ActionType<AcknowledgedResponse> {
    public static MLMcpMessageDispatchAction INSTANCE = new MLMcpMessageDispatchAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp/message/dispatch";

    private MLMcpMessageDispatchAction() {
        super(NAME, AcknowledgedResponse::new);
    }

}

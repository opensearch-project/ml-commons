/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import org.opensearch.action.ActionType;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;

public class MLMcpMessageAction extends ActionType<AcknowledgedResponse> {
    public static MLMcpMessageAction INSTANCE = new MLMcpMessageAction();
    public static final String NAME = "cluster:admin/opensearch/ml/mcp/message";

    private MLMcpMessageAction() {
        super(NAME, AcknowledgedResponse::new);
    }

}

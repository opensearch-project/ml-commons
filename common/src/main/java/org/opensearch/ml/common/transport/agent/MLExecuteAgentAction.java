/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.agent;

import org.opensearch.action.ActionType;

/**
 * ML execute agent action.
 */
public class MLExecuteAgentAction extends ActionType<MLExecuteAgentResponse> {
    public static final MLExecuteAgentAction INSTANCE = new MLExecuteAgentAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agents/execute";

    private MLExecuteAgentAction() {
        super(NAME, MLExecuteAgentResponse::new);
    }

}

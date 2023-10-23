/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import org.opensearch.action.ActionType;

public class MLRegisterAgentAction extends ActionType<MLRegisterAgentResponse> {
    public static MLRegisterAgentAction INSTANCE = new MLRegisterAgentAction();
    public static final String NAME = "cluster:admin/opensearch/ml/register_agent";

    private MLRegisterAgentAction() {
        super(NAME, MLRegisterAgentResponse::new);
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import org.opensearch.action.ActionType;

public class MLAgentGetAction extends ActionType<MLAgentGetResponse> {
    public static final MLAgentGetAction INSTANCE = new MLAgentGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agents/get";

    private MLAgentGetAction() { super(NAME, MLAgentGetResponse::new);}

}

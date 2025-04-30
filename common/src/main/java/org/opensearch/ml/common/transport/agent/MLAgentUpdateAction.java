/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLAgentUpdateAction extends ActionType<UpdateResponse> {
    public static final MLAgentUpdateAction INSTANCE = new MLAgentUpdateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agents/update";

    private MLAgentUpdateAction() {
        super(NAME, UpdateResponse::new);
    }
}

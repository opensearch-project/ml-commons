/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLAgentDeleteAction extends ActionType<DeleteResponse> {
    public static final MLAgentDeleteAction INSTANCE = new MLAgentDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agents/delete";

    private MLAgentDeleteAction() { super(NAME, DeleteResponse::new);}
}

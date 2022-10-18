/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.load;

import org.opensearch.action.ActionType;

public class MLLoadModelOnNodeAction extends ActionType<LoadModelNodesResponse> {
    public static MLLoadModelOnNodeAction INSTANCE = new MLLoadModelOnNodeAction();
    public static final String NAME = "cluster:admin/opensearch/ml/load_model_on_nodes";

    private MLLoadModelOnNodeAction() {
        super(NAME, LoadModelNodesResponse::new);
    }

}

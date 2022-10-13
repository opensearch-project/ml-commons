/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.unload;

import org.opensearch.action.ActionType;

public class MLUnloadModelAction extends ActionType<UnloadModelNodesResponse> {
    public static MLUnloadModelAction INSTANCE = new MLUnloadModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/unload_model";

    private MLUnloadModelAction() {
        super(NAME, UnloadModelNodesResponse::new);
    }

}

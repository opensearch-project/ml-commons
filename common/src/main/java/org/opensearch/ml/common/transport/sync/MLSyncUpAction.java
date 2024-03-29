/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import org.opensearch.action.ActionType;

public class MLSyncUpAction extends ActionType<MLSyncUpNodesResponse> {
    public static MLSyncUpAction INSTANCE = new MLSyncUpAction();
    public static final String NAME = "cluster:admin/opensearch/mlinternal/syncup";

    private MLSyncUpAction() {
        super(NAME, MLSyncUpNodesResponse::new);
    }

}

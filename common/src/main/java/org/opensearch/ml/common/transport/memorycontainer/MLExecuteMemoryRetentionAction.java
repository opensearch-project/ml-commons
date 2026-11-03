/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import org.opensearch.action.ActionType;

/**
 * Action to trigger the memory retention job on demand, instead of waiting for the scheduled
 * interval. This runs the real retention pipeline (it deletes per policy), reusing the exact same
 * code path as the scheduled run.
 */
public class MLExecuteMemoryRetentionAction extends ActionType<MLExecuteMemoryRetentionResponse> {
    public static final MLExecuteMemoryRetentionAction INSTANCE = new MLExecuteMemoryRetentionAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/retention/execute";

    private MLExecuteMemoryRetentionAction() {
        super(NAME, MLExecuteMemoryRetentionResponse::new);
    }
}

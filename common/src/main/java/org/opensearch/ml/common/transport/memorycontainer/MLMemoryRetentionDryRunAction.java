/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import org.opensearch.action.ActionType;

public class MLMemoryRetentionDryRunAction extends ActionType<MLMemoryRetentionDryRunResponse> {
    public static final MLMemoryRetentionDryRunAction INSTANCE = new MLMemoryRetentionDryRunAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/retention_dry_run";

    private MLMemoryRetentionDryRunAction() {
        super(NAME, MLMemoryRetentionDryRunResponse::new);
    }
}

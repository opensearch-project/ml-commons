/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.task;

import org.opensearch.action.ActionType;

public class MLCancelBatchJobAction extends ActionType<MLCancelBatchJobResponse> {
    public static final MLCancelBatchJobAction INSTANCE = new MLCancelBatchJobAction();
    public static final String NAME = "cluster:admin/opensearch/ml/tasks/cancel";

    private MLCancelBatchJobAction() {
        super(NAME, MLCancelBatchJobResponse::new);
    }
}

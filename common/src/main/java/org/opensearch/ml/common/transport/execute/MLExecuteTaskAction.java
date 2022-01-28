/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.execute;

import org.opensearch.action.ActionType;

public class MLExecuteTaskAction extends ActionType<MLExecuteTaskResponse> {
    public static final MLExecuteTaskAction INSTANCE = new MLExecuteTaskAction();
    public static final String NAME = "cluster:admin/opensearch/ml/execute";

    private MLExecuteTaskAction() {
        super(NAME, MLExecuteTaskResponse::new);
    }
}

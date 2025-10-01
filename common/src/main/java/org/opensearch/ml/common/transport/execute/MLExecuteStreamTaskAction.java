/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.execute;

import org.opensearch.action.ActionType;

public class MLExecuteStreamTaskAction extends ActionType<MLExecuteTaskResponse> {
    public static final MLExecuteStreamTaskAction INSTANCE = new MLExecuteStreamTaskAction();
    public static final String NAME = "cluster:admin/opensearch/ml/execute/stream";

    private MLExecuteStreamTaskAction() {
        super(NAME, MLExecuteTaskResponse::new);
    }
}

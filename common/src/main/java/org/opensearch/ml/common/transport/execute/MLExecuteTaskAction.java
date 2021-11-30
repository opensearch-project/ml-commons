/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
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

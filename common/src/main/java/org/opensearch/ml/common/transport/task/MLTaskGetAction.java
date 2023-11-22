/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.task;

import org.opensearch.action.ActionType;

public class MLTaskGetAction extends ActionType<MLTaskGetResponse> {
    public static final MLTaskGetAction INSTANCE = new MLTaskGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/tasks/get";

    private MLTaskGetAction() {
        super(NAME, MLTaskGetResponse::new);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import org.opensearch.action.ActionType;

public class MLMemoryGetAction extends ActionType<MLMemoryGetResponse> {
    public static final MLMemoryGetAction INSTANCE = new MLMemoryGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory/get";

    private MLMemoryGetAction() {
        super(NAME, MLMemoryGetResponse::new);
    }
}

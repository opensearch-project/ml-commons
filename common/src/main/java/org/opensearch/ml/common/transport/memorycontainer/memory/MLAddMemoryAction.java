/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;

public class MLAddMemoryAction extends ActionType<MLAddMemoryResponse> {
    public static final MLAddMemoryAction INSTANCE = new MLAddMemoryAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory/add";

    private MLAddMemoryAction() {
        super(NAME, MLAddMemoryResponse::new);
    }
}

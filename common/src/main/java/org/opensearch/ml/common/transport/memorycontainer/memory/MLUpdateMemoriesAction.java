/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateMemoriesAction extends ActionType<UpdateResponse> {
    public static final MLUpdateMemoriesAction INSTANCE = new MLUpdateMemoriesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory/update";

    private MLUpdateMemoriesAction() {
        super(NAME, UpdateResponse::new);
    }
}

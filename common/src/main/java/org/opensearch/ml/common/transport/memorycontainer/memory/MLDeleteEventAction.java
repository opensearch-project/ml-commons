/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

/**
 * Action for deleting an event from a memory container
 */
public class MLDeleteEventAction extends ActionType<DeleteResponse> {
    public static final MLDeleteEventAction INSTANCE = new MLDeleteEventAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_container/events/delete";

    private MLDeleteEventAction() {
        super(NAME, DeleteResponse::new);
    }
}

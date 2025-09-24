/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;

/**
 * Action for retrieving an event from a memory container
 */
public class MLGetEventAction extends ActionType<MLGetEventResponse> {
    public static final MLGetEventAction INSTANCE = new MLGetEventAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_container/events/get";

    private MLGetEventAction() {
        super(NAME, MLGetEventResponse::new);
    }
}

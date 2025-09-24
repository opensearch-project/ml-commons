/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import org.opensearch.action.ActionType;

public class MLCreateEventAction extends ActionType<MLCreateEventResponse> {
    public static final MLCreateEventAction INSTANCE = new MLCreateEventAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory_containers/events/create";

    private MLCreateEventAction() {
        super(NAME, MLCreateEventResponse::new);
    }
}

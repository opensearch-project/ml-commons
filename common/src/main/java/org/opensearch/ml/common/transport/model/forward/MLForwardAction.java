/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.forward;

import org.opensearch.action.ActionType;

public class MLForwardAction extends ActionType<MLForwardResponse> {
    public static MLForwardAction INSTANCE = new MLForwardAction();
    public static final String NAME = "cluster:admin/opensearch/ml/forward";

    private MLForwardAction() {
        super(NAME, MLForwardResponse::new);
    }

}

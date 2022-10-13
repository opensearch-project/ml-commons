/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.load;

import org.opensearch.action.ActionType;

public class MLLoadModelAction extends ActionType<LoadModelResponse> {
    public static MLLoadModelAction INSTANCE = new MLLoadModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/load_model";

    private MLLoadModelAction() {
        super(NAME, LoadModelResponse::new);
    }

}

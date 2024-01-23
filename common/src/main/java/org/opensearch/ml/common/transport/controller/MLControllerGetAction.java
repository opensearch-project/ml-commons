/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import org.opensearch.action.ActionType;

public class MLControllerGetAction extends ActionType<MLControllerGetResponse> {
    public static final MLControllerGetAction INSTANCE = new MLControllerGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/controllers/get";

    private MLControllerGetAction() {
        super(NAME, MLControllerGetResponse::new);
    }
}

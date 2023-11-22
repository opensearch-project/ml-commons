/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import org.opensearch.action.ActionType;

public class MLModelGetAction extends ActionType<MLModelGetResponse> {
    public static final MLModelGetAction INSTANCE = new MLModelGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/models/get";

    private MLModelGetAction() {
        super(NAME, MLModelGetResponse::new);
    }
}

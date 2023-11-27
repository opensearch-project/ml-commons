/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.opensearch.action.ActionType;

public class MLModelGroupGetAction extends ActionType<MLModelGroupGetResponse> {
    public static final MLModelGroupGetAction INSTANCE = new MLModelGroupGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/model_groups/get";

    private MLModelGroupGetAction() { super(NAME, MLModelGroupGetResponse::new);}
}
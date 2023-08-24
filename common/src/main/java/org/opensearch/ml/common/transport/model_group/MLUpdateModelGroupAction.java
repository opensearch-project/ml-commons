/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.opensearch.action.ActionType;

public class MLUpdateModelGroupAction extends ActionType<MLUpdateModelGroupResponse> {
    public static MLUpdateModelGroupAction INSTANCE = new MLUpdateModelGroupAction();
    public static final String NAME = "cluster:admin/opensearch/ml/update_model_group";

    private MLUpdateModelGroupAction() {
        super(NAME, MLUpdateModelGroupResponse::new);
    }
}

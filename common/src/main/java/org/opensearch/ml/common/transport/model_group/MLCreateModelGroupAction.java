/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.opensearch.action.ActionType;

public class MLCreateModelGroupAction extends ActionType<MLCreateModelGroupResponse> {
    public static MLCreateModelGroupAction INSTANCE = new MLCreateModelGroupAction();
    public static final String NAME = "cluster:admin/opensearch/ml/create_model_group";

    private MLCreateModelGroupAction() {
        super(NAME, MLCreateModelGroupResponse::new);
    }

}

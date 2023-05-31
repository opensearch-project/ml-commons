/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.opensearch.action.ActionType;

public class MLRegisterModelGroupAction extends ActionType<MLRegisterModelGroupResponse> {
    public static MLRegisterModelGroupAction INSTANCE = new MLRegisterModelGroupAction();
    public static final String NAME = "cluster:admin/opensearch/ml/register_model_group";

    private MLRegisterModelGroupAction() {
        super(NAME, MLRegisterModelGroupResponse::new);
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.register;

import org.opensearch.action.ActionType;

public class MLRegisterModelAction extends ActionType<MLRegisterModelResponse> {
    public static MLRegisterModelAction INSTANCE = new MLRegisterModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/register_model";

    private MLRegisterModelAction() {
        super(NAME, MLRegisterModelResponse::new);
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import org.opensearch.action.ActionType;

public class MLRegisterModelMetaAction extends ActionType<MLRegisterModelMetaResponse> {
    public static MLRegisterModelMetaAction INSTANCE = new MLRegisterModelMetaAction();
    public static final String NAME = "cluster:admin/opensearch/ml/register_model_meta";

    private MLRegisterModelMetaAction() {
        super(NAME, MLRegisterModelMetaResponse::new);
    }

}

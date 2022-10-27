/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import org.opensearch.action.ActionType;

public class MLCreateModelMetaAction extends ActionType<MLCreateModelMetaResponse> {
    public static MLCreateModelMetaAction INSTANCE = new MLCreateModelMetaAction();
    public static final String NAME = "cluster:admin/opensearch/ml/upload_custom_model_meta";

    private MLCreateModelMetaAction() {
        super(NAME, MLCreateModelMetaResponse::new);
    }

}
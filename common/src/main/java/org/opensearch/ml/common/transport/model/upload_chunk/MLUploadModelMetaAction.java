/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.upload_chunk;

import org.opensearch.action.ActionType;

public class MLUploadModelMetaAction extends ActionType<MLUploadModelMetaResponse> {
    public static MLUploadModelMetaAction INSTANCE = new MLUploadModelMetaAction();
    public static final String NAME = "cluster:admin/opensearch/ml/upload_custom_model_meta";

    private MLUploadModelMetaAction() {
        super(NAME, MLUploadModelMetaResponse::new);
    }

}
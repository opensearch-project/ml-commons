/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import org.opensearch.action.ActionType;

public class MLUploadModelChunkAction extends ActionType<MLUploadModelChunkResponse> {
    public static MLUploadModelChunkAction INSTANCE = new MLUploadModelChunkAction();
    public static final String NAME = "cluster:admin/opensearch/ml/upload_model_chunk";

    private MLUploadModelChunkAction() {
        super(NAME, MLUploadModelChunkResponse::new);
    }

}

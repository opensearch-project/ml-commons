/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom.upload;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.custom.load.LoadModelResponse;

public class MLUploadModelAction extends ActionType<LoadModelResponse> {
    public static MLUploadModelAction INSTANCE = new MLUploadModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/upload_custom_model";

    private MLUploadModelAction() {
        super(NAME, LoadModelResponse::new);
    }

}

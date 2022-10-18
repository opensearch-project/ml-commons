/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload;

import org.opensearch.action.ActionType;

public class MLUploadModelAction extends ActionType<UploadModelResponse> {
    public static MLUploadModelAction INSTANCE = new MLUploadModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/upload_model";

    private MLUploadModelAction() {
        super(NAME, UploadModelResponse::new);
    }

}

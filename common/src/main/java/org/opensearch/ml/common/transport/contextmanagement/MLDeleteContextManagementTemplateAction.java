/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

import org.opensearch.action.ActionType;

public class MLDeleteContextManagementTemplateAction extends ActionType<MLDeleteContextManagementTemplateResponse> {
    public static MLDeleteContextManagementTemplateAction INSTANCE = new MLDeleteContextManagementTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/context_management/delete";

    private MLDeleteContextManagementTemplateAction() {
        super(NAME, MLDeleteContextManagementTemplateResponse::new);
    }
}

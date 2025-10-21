/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

import org.opensearch.action.ActionType;

public class MLGetContextManagementTemplateAction extends ActionType<MLGetContextManagementTemplateResponse> {
    public static MLGetContextManagementTemplateAction INSTANCE = new MLGetContextManagementTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/context_management/get";

    private MLGetContextManagementTemplateAction() {
        super(NAME, MLGetContextManagementTemplateResponse::new);
    }
}

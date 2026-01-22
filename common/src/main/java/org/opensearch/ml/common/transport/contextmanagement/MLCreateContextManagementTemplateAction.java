/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

import org.opensearch.action.ActionType;

public class MLCreateContextManagementTemplateAction extends ActionType<MLCreateContextManagementTemplateResponse> {
    public static MLCreateContextManagementTemplateAction INSTANCE = new MLCreateContextManagementTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/context_management/create";

    private MLCreateContextManagementTemplateAction() {
        super(NAME, MLCreateContextManagementTemplateResponse::new);
    }
}

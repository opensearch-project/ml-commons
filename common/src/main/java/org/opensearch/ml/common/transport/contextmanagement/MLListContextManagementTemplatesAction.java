/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

import org.opensearch.action.ActionType;

public class MLListContextManagementTemplatesAction extends ActionType<MLListContextManagementTemplatesResponse> {
    public static MLListContextManagementTemplatesAction INSTANCE = new MLListContextManagementTemplatesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/context_management/list";

    private MLListContextManagementTemplatesAction() {
        super(NAME, MLListContextManagementTemplatesResponse::new);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateContextManagementTemplateAction extends ActionType<UpdateResponse> {
    public static final MLUpdateContextManagementTemplateAction INSTANCE = new MLUpdateContextManagementTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/context_management/update";

    private MLUpdateContextManagementTemplateAction() {
        super(NAME, UpdateResponse::new);
    }
}

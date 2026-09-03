/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

import org.opensearch.action.ActionType;

public class MLDeleteAgenticSearchTemplateAction extends ActionType<MLDeleteAgenticSearchTemplateResponse> {
    public static MLDeleteAgenticSearchTemplateAction INSTANCE = new MLDeleteAgenticSearchTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agentic_search_templates/delete";

    private MLDeleteAgenticSearchTemplateAction() {
        super(NAME, MLDeleteAgenticSearchTemplateResponse::new);
    }
}

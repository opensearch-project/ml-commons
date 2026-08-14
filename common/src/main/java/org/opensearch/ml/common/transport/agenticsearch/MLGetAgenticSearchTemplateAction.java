/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

import org.opensearch.action.ActionType;

public class MLGetAgenticSearchTemplateAction extends ActionType<MLGetAgenticSearchTemplateResponse> {
    public static MLGetAgenticSearchTemplateAction INSTANCE = new MLGetAgenticSearchTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agentic_search_templates/get";

    private MLGetAgenticSearchTemplateAction() {
        super(NAME, MLGetAgenticSearchTemplateResponse::new);
    }
}

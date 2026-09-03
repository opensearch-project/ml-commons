/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

import org.opensearch.action.ActionType;

public class MLListAgenticSearchTemplatesAction extends ActionType<MLListAgenticSearchTemplatesResponse> {
    public static MLListAgenticSearchTemplatesAction INSTANCE = new MLListAgenticSearchTemplatesAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agentic_search_templates/list";

    private MLListAgenticSearchTemplatesAction() {
        super(NAME, MLListAgenticSearchTemplatesResponse::new);
    }
}

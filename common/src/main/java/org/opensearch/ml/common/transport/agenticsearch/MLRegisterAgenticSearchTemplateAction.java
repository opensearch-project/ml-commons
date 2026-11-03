/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

import org.opensearch.action.ActionType;

public class MLRegisterAgenticSearchTemplateAction extends ActionType<MLRegisterAgenticSearchTemplateResponse> {
    public static MLRegisterAgenticSearchTemplateAction INSTANCE = new MLRegisterAgenticSearchTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agentic_search_templates/register";

    private MLRegisterAgenticSearchTemplateAction() {
        super(NAME, MLRegisterAgenticSearchTemplateResponse::new);
    }
}

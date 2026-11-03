/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateAgenticSearchTemplateAction extends ActionType<UpdateResponse> {
    public static MLUpdateAgenticSearchTemplateAction INSTANCE = new MLUpdateAgenticSearchTemplateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/agentic_search_templates/update";

    private MLUpdateAgenticSearchTemplateAction() {
        super(NAME, UpdateResponse::new);
    }
}

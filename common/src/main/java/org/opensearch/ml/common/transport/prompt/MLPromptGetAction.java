/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import org.opensearch.action.ActionType;

public class MLPromptGetAction extends ActionType<MLPromptGetResponse> {
    public static final MLPromptGetAction INSTANCE = new MLPromptGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/prompts/get";

    private MLPromptGetAction() {
        super(NAME, MLPromptGetResponse::new);
    }
}

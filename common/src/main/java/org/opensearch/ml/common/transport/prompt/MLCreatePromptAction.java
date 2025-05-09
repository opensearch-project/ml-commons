/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import org.opensearch.action.ActionType;

public class MLCreatePromptAction extends ActionType<MLCreatePromptResponse> {
    public static MLCreatePromptAction INSTANCE = new MLCreatePromptAction();
    public static final String NAME = "cluster:admin/opensearch/ml/create_prompt";

    private MLCreatePromptAction() {
        super(NAME, MLCreatePromptResponse::new);
    }
}

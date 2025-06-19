/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import org.opensearch.action.ActionType;

public class MLImportPromptAction extends ActionType<MLImportPromptResponse> {
    public static MLImportPromptAction INSTANCE = new MLImportPromptAction();
    public static final String NAME = "cluster:admin/opensearch/ml/import";

    private MLImportPromptAction() {
        super(NAME, MLImportPromptResponse::new);
    }
}

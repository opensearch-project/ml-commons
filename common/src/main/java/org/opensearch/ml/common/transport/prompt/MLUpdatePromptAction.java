/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdatePromptAction extends ActionType<UpdateResponse> {
    public static MLUpdatePromptAction INSTANCE = new MLUpdatePromptAction();
    public static final String NAME = "cluster:admin/opensearch/ml/prompts/update";

    private MLUpdatePromptAction() {
        super(NAME, UpdateResponse::new);
    }
}

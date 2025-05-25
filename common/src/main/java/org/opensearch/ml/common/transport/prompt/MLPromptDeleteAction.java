/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLPromptDeleteAction extends ActionType<DeleteResponse> {
    public static final MLPromptDeleteAction INSTANCE = new MLPromptDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/prompts/delete";

    private MLPromptDeleteAction() {
        super(NAME, DeleteResponse::new);
    }
}

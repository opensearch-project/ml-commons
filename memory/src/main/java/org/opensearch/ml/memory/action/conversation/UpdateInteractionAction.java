/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class UpdateInteractionAction extends ActionType<UpdateResponse> {
    public static final UpdateInteractionAction INSTANCE = new UpdateInteractionAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory/interaction/update";

    private UpdateInteractionAction() {
        super(NAME, UpdateResponse::new);
    }

}

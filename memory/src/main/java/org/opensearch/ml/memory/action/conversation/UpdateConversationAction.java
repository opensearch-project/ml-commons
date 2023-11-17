/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class UpdateConversationAction extends ActionType<UpdateResponse> {
    public static final UpdateConversationAction INSTANCE = new UpdateConversationAction();
    public static final String NAME = "cluster:admin/opensearch/ml/memory/conversation/update";

    private UpdateConversationAction() {
        super(NAME, UpdateResponse::new);
    }
}

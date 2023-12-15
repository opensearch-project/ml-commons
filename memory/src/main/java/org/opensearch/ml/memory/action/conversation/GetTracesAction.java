/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import org.opensearch.action.ActionType;

/**
 * Action to return the traces associated with an interaction
 */
public class GetTracesAction extends ActionType<GetTracesResponse> {
    /** Instance of this */
    public static final GetTracesAction INSTANCE = new GetTracesAction();
    /** Name of this action */
    public static final String NAME = "cluster:admin/opensearch/ml/memory/trace/get";

    private GetTracesAction() {
        super(NAME, GetTracesResponse::new);
    }

}

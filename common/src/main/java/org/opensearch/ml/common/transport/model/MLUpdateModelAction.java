/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateModelAction extends ActionType<UpdateResponse> {
    public static MLUpdateModelAction INSTANCE = new MLUpdateModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/models/update";

    private MLUpdateModelAction() {
        super(NAME, UpdateResponse::new);
    }
}

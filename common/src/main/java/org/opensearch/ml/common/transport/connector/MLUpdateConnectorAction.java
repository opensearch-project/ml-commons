/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateConnectorAction extends ActionType<UpdateResponse> {
    public static final MLUpdateConnectorAction INSTANCE = new MLUpdateConnectorAction();
    public static final String NAME = "cluster:admin/opensearch/ml/connectors/update";

    private MLUpdateConnectorAction() {
        super(NAME, UpdateResponse::new);
    }
}

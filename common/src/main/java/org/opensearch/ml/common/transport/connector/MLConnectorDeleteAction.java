/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

public class MLConnectorDeleteAction extends ActionType<DeleteResponse> {
    public static final MLConnectorDeleteAction INSTANCE = new MLConnectorDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/connectors/delete";

    private MLConnectorDeleteAction() {
        super(NAME, DeleteResponse::new);
    }
}

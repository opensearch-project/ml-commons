/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.opensearch.action.ActionType;

public class MLConnectorGetAction extends ActionType<MLConnectorGetResponse> {
    public static final MLConnectorGetAction INSTANCE = new MLConnectorGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/connectors/get";

    private MLConnectorGetAction() {
        super(NAME, MLConnectorGetResponse::new);
    }

}

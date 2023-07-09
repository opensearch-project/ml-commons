/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.opensearch.action.ActionType;

public class MLCreateConnectorAction extends ActionType<MLCreateConnectorResponse> {
    public static MLCreateConnectorAction INSTANCE = new MLCreateConnectorAction();
    public static final String NAME = "cluster:admin/opensearch/ml/create_connector";

    private MLCreateConnectorAction() {
        super(NAME, MLCreateConnectorResponse::new);
    }
}

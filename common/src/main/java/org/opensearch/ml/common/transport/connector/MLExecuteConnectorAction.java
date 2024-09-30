/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.MLTaskResponse;

public class MLExecuteConnectorAction extends ActionType<MLTaskResponse> {
    public static final MLExecuteConnectorAction INSTANCE = new MLExecuteConnectorAction();
    public static final String NAME = "cluster:admin/opensearch/ml/connectors/execute";

    private MLExecuteConnectorAction() {
        super(NAME, MLTaskResponse::new);
    }
}

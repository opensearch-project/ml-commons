/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.tools;

import org.opensearch.action.ActionType;

public class MLGetToolAction extends ActionType<MLToolGetResponse> {
    public static final MLGetToolAction INSTANCE = new MLGetToolAction();
    public static final String NAME = "cluster:admin/opensearch/ml/tools/get";

    public MLGetToolAction() {
        super(NAME, MLToolGetResponse::new);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionType;

public class MLIndexInsightContainerPutAction extends ActionType<MLIndexInsightContainerPutResponse> {
    public static final MLIndexInsightContainerPutAction INSTANCE = new MLIndexInsightContainerPutAction();
    public static final String NAME = "cluster:admin/opensearch/ml/indexInsightContainer/put";

    public MLIndexInsightContainerPutAction() {
        super(NAME, MLIndexInsightContainerPutResponse::new);
    }
}

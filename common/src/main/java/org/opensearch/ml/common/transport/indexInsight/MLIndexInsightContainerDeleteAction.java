/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionType;

public class MLIndexInsightContainerDeleteAction extends ActionType<MLIndexInsightContainerDeleteResponse> {
    public static final MLIndexInsightContainerDeleteAction INSTANCE = new MLIndexInsightContainerDeleteAction();
    public static final String NAME = "cluster:admin/opensearch/ml/indexInsightContainer/delete";

    public MLIndexInsightContainerDeleteAction() {
        super(NAME, MLIndexInsightContainerDeleteResponse::new);
    }
}

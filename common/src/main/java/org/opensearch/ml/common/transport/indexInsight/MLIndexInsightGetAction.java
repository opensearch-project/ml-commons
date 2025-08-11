/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionType;

public class MLIndexInsightGetAction extends ActionType<MLIndexInsightGetResponse> {
    public static final MLIndexInsightGetAction INSTANCE = new MLIndexInsightGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/indexInsight/get";

    public MLIndexInsightGetAction() {
        super(NAME, MLIndexInsightGetResponse::new);
    }
}

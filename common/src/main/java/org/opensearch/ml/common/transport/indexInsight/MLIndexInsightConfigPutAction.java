/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionType;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;

public class MLIndexInsightConfigPutAction extends ActionType<AcknowledgedResponse> {
    public static final MLIndexInsightConfigPutAction INSTANCE = new MLIndexInsightConfigPutAction();
    public static final String NAME = "cluster:admin/opensearch/ml/index_insight_container/create";

    public MLIndexInsightConfigPutAction() {
        super(NAME, AcknowledgedResponse::new);
    }
}

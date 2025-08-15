/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionType;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;

public class MLIndexInsightContainerCreateAction extends ActionType<AcknowledgedResponse> {
    public static final MLIndexInsightContainerCreateAction INSTANCE = new MLIndexInsightContainerCreateAction();
    public static final String NAME = "cluster:admin/opensearch/ml/index_insight_container/create";

    public MLIndexInsightContainerCreateAction() {
        super(NAME, AcknowledgedResponse::new);
    }
}

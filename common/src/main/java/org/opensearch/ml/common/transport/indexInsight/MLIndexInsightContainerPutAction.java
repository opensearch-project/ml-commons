/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionType;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;

public class MLIndexInsightContainerPutAction extends ActionType<AcknowledgedResponse> {
    public static final MLIndexInsightContainerPutAction INSTANCE = new MLIndexInsightContainerPutAction();
    public static final String NAME = "cluster:admin/opensearch/ml/indexInsightContainer/put";

    public MLIndexInsightContainerPutAction() {
        super(NAME, AcknowledgedResponse::new);
    }
}

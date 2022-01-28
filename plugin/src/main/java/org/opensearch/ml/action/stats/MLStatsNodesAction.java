/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import org.opensearch.action.ActionType;
import org.opensearch.ml.constant.CommonValue;

public class MLStatsNodesAction extends ActionType<MLStatsNodesResponse> {
    // Internal Action which is not used for public facing RestAPIs.
    public static final String NAME = CommonValue.ACTION_PREFIX + "stats/nodes";
    public static final MLStatsNodesAction INSTANCE = new MLStatsNodesAction();

    /**
     * Constructor
     */
    private MLStatsNodesAction() {
        super(NAME, MLStatsNodesResponse::new);
    }
}

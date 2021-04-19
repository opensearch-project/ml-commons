/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */


package org.opensearch.ml.action.stats;

import org.opensearch.ml.constant.CommonValue;
import org.opensearch.action.ActionType;

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

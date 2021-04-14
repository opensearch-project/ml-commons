package org.opensearch.ml.action.stats;

import org.opensearch.ml.constant.CommonValue;
import org.opensearch.action.ActionType;

public class MLStatsNodesAction extends ActionType<MLStatsNodesResponse> {
    // Internal Action which is not used for public facing RestAPIs.
    public static final String NAME = CommonValue.INTERNAL_ACTION_PREFIX + "stats/nodes";
    public static final MLStatsNodesAction INSTANCE = new MLStatsNodesAction();

    /**
     * Constructor
     */
    private MLStatsNodesAction() {
        super(NAME, MLStatsNodesResponse::new);
    }
}

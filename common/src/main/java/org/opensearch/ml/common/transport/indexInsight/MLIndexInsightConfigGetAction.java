package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionType;

public class MLIndexInsightConfigGetAction extends ActionType<MLIndexInsightConfigGetResponse> {
    public static final MLIndexInsightConfigPutAction INSTANCE = new MLIndexInsightConfigPutAction();
    public static final String NAME = "cluster:admin/opensearch/ml/index_insight_config/get";

    public MLIndexInsightConfigGetAction() {
        super(NAME, MLIndexInsightConfigGetResponse::new);
    }
}

package org.opensearch.ml.common.transport.indexInsight;

import org.opensearch.action.ActionType;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.ml.common.transport.connector.MLConnectorGetAction;
import org.opensearch.ml.common.transport.connector.MLConnectorGetResponse;

public class MLIndexInsightGetAction extends ActionType<MLIndexInsightGetResponse> {
    public static final MLIndexInsightGetAction INSTANCE = new MLIndexInsightGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/indexInsight/get";

    public MLIndexInsightGetAction() {
        super(NAME, MLIndexInsightGetResponse::new);
    }
}

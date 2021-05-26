package org.opensearch.ml.action.prediction;

import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.action.ActionType;

public class MLPredictionTaskRemoteExecutionAction extends ActionType<MLPredictionTaskResponse> {
    public static MLPredictionTaskRemoteExecutionAction INSTANCE = new MLPredictionTaskRemoteExecutionAction();
    public static final String NAME = "cluster:admin/opensearch-ml/predict/" + "ml_task_remote";

    private MLPredictionTaskRemoteExecutionAction() {
        super(NAME, MLPredictionTaskResponse::new);
    }
}

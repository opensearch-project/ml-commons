package org.opensearch.ml.common.transport.prompt;

import org. opensearch.action.ActionType;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;

public class MLCreatePromptAction extends ActionType<MLCreatePromptResponse> {
    public static MLCreatePromptAction INSTANCE = new MLCreatePromptAction();
    public static final String NAME = "cluster:admin/opensearch/ml/create_prompt";

    private MLCreatePromptAction() {
        super(NAME, MLCreatePromptResponse::new);
    }
}

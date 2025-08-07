package org.opensearch.ml.common.indexInsight;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.transport.client.Client;

import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_AGNET_NAME;

public class IndexInsightUtils {
    public static void getAgentIdToRun(Client client, String tenantId, ActionListener<String> actionListener){
        MLConfigGetRequest mlConfigGetRequest =
                new MLConfigGetRequest(INDEX_INSIGHT_AGNET_NAME, tenantId);
        client.execute(MLConfigGetAction.INSTANCE, mlConfigGetRequest, ActionListener.wrap(r -> {
            MLConfig mlConfig = r.getMlConfig();
            actionListener.onResponse(mlConfig.getConfiguration().getAgentId());
        }, actionListener::onFailure));
    }
}

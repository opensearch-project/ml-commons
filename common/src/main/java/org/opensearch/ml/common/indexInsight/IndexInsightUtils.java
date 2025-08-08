package org.opensearch.ml.common.indexInsight;

import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_AGENT_NAME;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.transport.client.Client;

public class IndexInsightUtils {
    public static void getAgentIdToRun(Client client, String tenantId, ActionListener<String> actionListener) {
        MLConfigGetRequest mlConfigGetRequest = new MLConfigGetRequest(INDEX_INSIGHT_AGENT_NAME, tenantId);
        client.execute(MLConfigGetAction.INSTANCE, mlConfigGetRequest, ActionListener.wrap(r -> {
            MLConfig mlConfig = r.getMlConfig();
            actionListener.onResponse(mlConfig.getConfiguration().getAgentId());
        }, actionListener::onFailure));
    }

    /**
     * Flatten all the fields in the mappings, insert the field to fieldType mapping to a map
     * @param mappingSource the mappings of an index
     * @param fieldsToType the result containing the field to fieldType mapping
     * @param prefix the parent field path
     * @param includeFields whether include the `fields` in a text type field, for some use case like PPLTool, `fields` in a text type field
     *                      cannot be included, but for CreateAnomalyDetectorTool, `fields` must be included.
     */
    public static void extractFieldNamesTypes(
        Map<String, Object> mappingSource,
        Map<String, String> fieldsToType,
        String prefix,
        boolean includeFields
    ) {
        if (prefix.length() > 0) {
            prefix += ".";
        }

        for (Map.Entry<String, Object> entry : mappingSource.entrySet()) {
            String n = entry.getKey();
            Object v = entry.getValue();

            if (v instanceof Map) {
                Map<String, Object> vMap = (Map<String, Object>) v;
                if (vMap.containsKey("type")) {
                    String fieldType = (String) vMap.getOrDefault("type", "");
                    // no need to extract alias into the result, and for object field, extract the subfields only
                    if (!fieldType.equals("alias") && !fieldType.equals("object")) {
                        fieldsToType.put(prefix + n, (String) vMap.get("type"));
                    }
                }
                if (vMap.containsKey("properties")) {
                    extractFieldNamesTypes((Map<String, Object>) vMap.get("properties"), fieldsToType, prefix + n, includeFields);
                }
                if (includeFields && vMap.containsKey("fields")) {
                    extractFieldNamesTypes((Map<String, Object>) vMap.get("fields"), fieldsToType, prefix + n, true);
                }
            }
        }
    }
}

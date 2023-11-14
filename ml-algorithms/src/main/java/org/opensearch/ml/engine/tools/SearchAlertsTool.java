/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.List;
import java.util.Map;

import org.opensearch.client.Client;
import org.opensearch.client.node.NodeClient;
import org.opensearch.commons.alerting.AlertingPluginInterface;
import org.opensearch.commons.alerting.action.GetAlertsRequest;
import org.opensearch.commons.alerting.action.GetAlertsResponse;
import org.opensearch.commons.alerting.model.Alert;
import org.opensearch.commons.alerting.model.Table;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;

@ToolAnnotation(SearchAlertsTool.TYPE)
public class SearchAlertsTool extends AbstractTool {
    public static final String TYPE = "SearchAlertsTool";
    private static final String DEFAULT_DESCRIPTION = "Use this tool to search alerts.";

    private Client client;

    public SearchAlertsTool(Client client) {
        super(TYPE, DEFAULT_DESCRIPTION);
        this.client = client;

        // probably keep this overridden output parser. need to ensure the output matches what's expected
        super.setOutputParser(new Parser<>() {
            @Override
            public Object parse(Object o) {
                @SuppressWarnings("unchecked")
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        });
    }

    @Override
    public <T> void run(Map<String, String> toolSpec, Map<String, String> parameters, ActionListener<T> listener) {
        final String tableSortOrder = parameters.getOrDefault("sortOrder", "asc");
        final String tableSortString = parameters.getOrDefault("sortString", "monitor_name.keyword");
        final int tableSize = parameters.containsKey("size") ? Integer.parseInt(parameters.get("size")) : 20;
        final int startIndex = parameters.containsKey("startIndex") ? Integer.parseInt(parameters.get("startIndex")) : 0;
        final String searchString = parameters.getOrDefault("searchString", null);

        // not exposing "missing" from the table, using default of null
        final Table table = new Table(tableSortOrder, tableSortString, null, tableSize, startIndex, searchString);

        final String severityLevel = parameters.getOrDefault("severityLevel", "ALL");
        final String alertState = parameters.getOrDefault("alertState", "ALL");
        final String monitorId = parameters.getOrDefault("monitorId", null);
        final String alertIndex = parameters.getOrDefault("alertIndex", null);
        @SuppressWarnings("unchecked")
        final List<String> monitorIds = parameters.containsKey("monitorIds")
            ? gson.fromJson(parameters.get("monitorIds"), List.class)
            : null;
        @SuppressWarnings("unchecked")
        final List<String> workflowIds = parameters.containsKey("workflowIds")
            ? gson.fromJson(parameters.get("workflowIds"), List.class)
            : null;
        @SuppressWarnings("unchecked")
        final List<String> alertIds = parameters.containsKey("alertIds") ? gson.fromJson(parameters.get("alertIds"), List.class) : null;

        GetAlertsRequest getAlertsRequest = new GetAlertsRequest(
            table,
            severityLevel,
            alertState,
            monitorId,
            alertIndex,
            monitorIds,
            workflowIds,
            alertIds
        );

        // create response listener
        // stringify the response, may change to a standard format in the future
        ActionListener<GetAlertsResponse> getAlertsListener = ActionListener.<GetAlertsResponse>wrap(response -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Alerts=[");
            for (Alert alert : response.getAlerts()) {
                sb.append(alert.toString());
            }
            sb.append("]");
            sb.append("TotalAlerts=").append(response.getTotalAlerts());
            listener.onResponse((T) sb.toString());
        }, e -> { listener.onFailure(e); });

        // execute the search
        AlertingPluginInterface.INSTANCE.getAlerts((NodeClient) client, getAlertsRequest, getAlertsListener);
    }

    @Override
    public boolean validate(Map<String, String> toolSpec, Map<String, String> parameters) {
        return true;
    }

}

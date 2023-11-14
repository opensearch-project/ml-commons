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
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.commons.alerting.AlertingPluginInterface;
import org.opensearch.commons.alerting.action.GetAlertsRequest;
import org.opensearch.commons.alerting.action.GetAlertsResponse;
import org.opensearch.commons.alerting.model.Table;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;

import lombok.Getter;
import lombok.Setter;

@ToolAnnotation(SearchAlertsTool.NAME)
public class SearchAlertsTool implements Tool {
    public static final String NAME = "SearchAlertsTool";
    private static final String DEFAULT_DESCRIPTION = "Use this tool to search alerts.";

    @Setter
    @Getter
    private String name = SearchAlertsTool.NAME;
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    @Getter
    private String type;
    @Getter
    private String version;

    private Client client;
    @Setter
    private Parser<?, ?> inputParser;
    @Setter
    private Parser<?, ?> outputParser;
    @SuppressWarnings("unused")
    private ClusterService clusterService;

    public SearchAlertsTool(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;

        // probably keep this overridden output parser. need to ensure the output matches what's expected
        outputParser = new Parser<>() {
            @Override
            public Object parse(Object o) {
                @SuppressWarnings("unchecked")
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        };
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {

        // build out the request
        final String tableSortOrder = parameters.containsKey("sortOrder") ? parameters.get("sortOrder") : "asc";
        final String tableSortString = parameters.containsKey("sortString") ? parameters.get("sortString") : "monitor_name.keyword";
        final int tableSize = parameters.containsKey("size") ? Integer.parseInt(parameters.get("size")) : 20;
        final String searchString = parameters.containsKey("searchString") ? parameters.get("searchString") : null;

        // not exposing "missing" or "startIndex" from the table, using defaults of null/0, respectively
        final Table table = new Table(tableSortOrder, tableSortString, null, tableSize, 0, searchString);

        final String severityLevel = parameters.containsKey("severityLevel") ? parameters.get("severityLevel") : "ALL";
        final String alertState = parameters.containsKey("alertState") ? parameters.get("alertState") : "ALL";
        final String monitorId = parameters.containsKey("monitorId") ? parameters.get("monitorId") : null;
        final String alertIndex = parameters.containsKey("alertIndex") ? parameters.get("alertIndex") : null;
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
        ActionListener<GetAlertsResponse> getAlertsListener = ActionListener.<GetAlertsResponse>wrap(response -> {
            listener.onResponse((T) response);
        }, e -> { listener.onFailure(e); });

        // execute the search
        AlertingPluginInterface.INSTANCE.getAlerts((NodeClient) client, getAlertsRequest, getAlertsListener);
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null) {
            return false;
        }
        return true;
    }

    /**
     * Factory for the {@link SearchAlertsTool}
     */
    public static class Factory implements Tool.Factory<SearchAlertsTool> {
        private Client client;
        private ClusterService clusterService;

        private static Factory INSTANCE;

        /** 
         * Create or return the singleton factory instance
         */
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (SearchAlertsTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        /**
         * Initialize this factory
         * @param client The OpenSearch client
         * @param clusterService The OpenSearch cluster service
         */
        public void init(Client client, ClusterService clusterService) {
            this.client = client;
            this.clusterService = clusterService;
        }

        @Override
        public SearchAlertsTool create(Map<String, Object> map) {
            return new SearchAlertsTool(client, clusterService);
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }

}
/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.Getter;
import lombok.Setter;

@ToolAnnotation(SearchMonitorsTool.TYPE)
public class SearchMonitorsTool implements Tool {
    public static final String TYPE = "SearchMonitorsTool";
    private static final String DEFAULT_DESCRIPTION = "Use this tool to search alerting monitors.";

    @Setter
    @Getter
    private String name = TYPE;
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

    public SearchMonitorsTool(Client client) {
        this.client = client;

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
        final String monitorId = parameters.getOrDefault("monitorId", null);
        final String monitorName = parameters.getOrDefault("monitorName", null);
        final String monitorNamePattern = parameters.getOrDefault("monitorNamePattern", null);
        final boolean enabled = parameters.containsKey("enabled") ? Boolean.parseBoolean(parameters.get("enabled")) : null;
        final boolean hasTriggers = parameters.containsKey("hasTriggers") ? Boolean.parseBoolean(parameters.get("hasTriggers")) : null;
        final String index = parameters.getOrDefault("index", null);
        final String sortOrder = parameters.getOrDefault("sortOrder", "asc");
        final String sortString = parameters.getOrDefault("sortString", "monitor.name.keyword");
        final int size = parameters.containsKey("size") ? Integer.parseInt(parameters.get("size")) : 20;
        final int startIndex = parameters.containsKey("startIndex") ? Integer.parseInt(parameters.get("startIndex")) : 0;

        QueryBuilder queryBuilder;
        if (monitorName == null) {
            queryBuilder = new MatchAllQueryBuilder();
        } else {
            queryBuilder = new MatchQueryBuilder("monitor.name", monitorName);
        }

        // generate the search request based on parameters
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder);

        SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder);

        /// SearchMonitorRequest searchMonitorRequest = new SearchMonitorRequest(searchRequest);

        // create response listener
        // stringify the response, may change to a standard format in the future
        ActionListener<SearchResponse> searchMonitorsListener = ActionListener.<SearchResponse>wrap(response -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Response placeholder");
            listener.onResponse((T) sb.toString());
        }, e -> { listener.onFailure(e); });

        // execute the search
        // AlertingPluginInterface.INSTANCE.searchMonitors((NodeClient) client, searchMonitorsRequest, searchMonitorsListener);
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Factory for the {@link SearchAlertsTool}
     */
    public static class Factory implements Tool.Factory<SearchAlertsTool> {
        private Client client;

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
         */
        public void init(Client client) {
            this.client = client;
        }

        @Override
        public SearchAlertsTool create(Map<String, Object> map) {
            return new SearchAlertsTool(client);
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }

}

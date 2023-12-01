/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.client.node.NodeClient;
import org.opensearch.commons.alerting.AlertingPluginInterface;
import org.opensearch.commons.alerting.action.SearchMonitorRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.ExistsQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.query.WildcardQueryBuilder;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

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
        final Boolean enabled = parameters.containsKey("enabled") ? Boolean.parseBoolean(parameters.get("enabled")) : null;
        final Boolean hasTriggers = parameters.containsKey("hasTriggers") ? Boolean.parseBoolean(parameters.get("hasTriggers")) : null;
        final String indices = parameters.getOrDefault("indices", null);
        final String sortOrderStr = parameters.getOrDefault("sortOrder", "asc");
        final SortOrder sortOrder = sortOrderStr == "asc" ? SortOrder.ASC : SortOrder.DESC;
        final String sortString = parameters.getOrDefault("sortString", "monitor.name.keyword");
        final int size = parameters.containsKey("size") ? Integer.parseInt(parameters.get("size")) : 20;
        final int startIndex = parameters.containsKey("startIndex") ? Integer.parseInt(parameters.get("startIndex")) : 0;

        // If a monitor ID is specified, all other params will be ignored. Simply return the monitor details based on that ID
        // via the get monitor transport action
        if (monitorId != null) {
            // TODO
        } else {
            List<QueryBuilder> mustList = new ArrayList<QueryBuilder>();
            if (monitorName != null) {
                mustList.add(new TermQueryBuilder("monitor.name.keyword", monitorName));
            }
            if (monitorNamePattern != null) {
                mustList.add(new WildcardQueryBuilder("monitor.name.keyword", monitorNamePattern));
            }
            if (enabled != null) {
                mustList.add(new TermQueryBuilder("monitor.enabled", enabled));
            }
            if (hasTriggers != null) {
                NestedQueryBuilder nestedTriggerQuery = new NestedQueryBuilder(
                    "monitor.triggers",
                    new ExistsQueryBuilder("monitor.triggers"),
                    null
                );
                BoolQueryBuilder triggerQuery = new BoolQueryBuilder();
                if (hasTriggers) {
                    triggerQuery.must(nestedTriggerQuery);
                } else {
                    triggerQuery.mustNot(nestedTriggerQuery);
                }
                mustList.add(triggerQuery);
            }
            if (indices != null) {
                mustList
                    .add(
                        new NestedQueryBuilder("monitor.inputs", new WildcardQueryBuilder("monitor.inputs.search.indices", indices), null)
                    );
            }

            BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
            boolQueryBuilder.must().addAll(mustList);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(boolQueryBuilder)
                .size(size)
                .from(startIndex)
                .sort(sortString, sortOrder);

            SearchMonitorRequest searchMonitorRequest = new SearchMonitorRequest(new SearchRequest().source(searchSourceBuilder));

            // create response listener
            // stringify the response, may change to a standard format in the future
            ActionListener<SearchResponse> searchMonitorListener = ActionListener.<SearchResponse>wrap(response -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Response placeholder");
                listener.onResponse((T) sb.toString());
            }, e -> { listener.onFailure(e); });

            // execute the search
            AlertingPluginInterface.INSTANCE.searchMonitors((NodeClient) client, searchMonitorRequest, searchMonitorListener);
        }
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
     * Factory for the {@link SearchMonitorsTool}
     */
    public static class Factory implements Tool.Factory<SearchMonitorsTool> {
        private Client client;

        private static Factory INSTANCE;

        /** 
         * Create or return the singleton factory instance
         */
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (SearchMonitorsTool.class) {
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
        public SearchMonitorsTool create(Map<String, Object> map) {
            return new SearchMonitorsTool(client);
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }

}

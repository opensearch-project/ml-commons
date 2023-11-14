/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.client.Requests;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.base.Strings;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;


@Log4j2
@ToolAnnotation(VisualizationsTool.TYPE)
public class VisualizationsTool extends AbstractTool {
    public static final String NAME = "Find Visualizations";
    public static final String TYPE = "VisualizationTool";
    public static final String VERSION = "v1.0";

    public static final String SAVED_OBJECT_TYPE = "visualization";
    private static final String DEFAULT_DESCRIPTION = "Use this tool to find user created visualizations. This tool takes the visualization name as input and returns the first 3 matching visualizations";
    private final Client client;

    @Builder
    public VisualizationsTool(Client client) {
        super(TYPE, NAME, DEFAULT_DESCRIPTION);
        this.client = client;
    }

    @Override
    public <T> void run(Map<String, String> toolSpec, Map<String, String> parameters, ActionListener<T> listener) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.termQuery("type", SAVED_OBJECT_TYPE));
        boolQueryBuilder.must().add(QueryBuilders.matchQuery(SAVED_OBJECT_TYPE + ".title", parameters.get("input")));

        SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource().query(boolQueryBuilder);
        searchSourceBuilder.from(0).size(3);
        SearchRequest searchRequest = Requests.searchRequest(getIndex(toolSpec)).source(searchSourceBuilder);

        client.search(searchRequest, new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse searchResponse) {
                SearchHits hits = searchResponse.getHits();
                StringBuilder visBuilder = new StringBuilder();
                visBuilder.append("Title,Id\n");
                if (hits.getTotalHits().value > 0) {
                    Arrays.stream(hits.getHits()).forEach(h -> {
                        String id = trimIdPrefix(h.getId());
                        Map<String, String> visMap = (Map<String, String>) h.getSourceAsMap().get(SAVED_OBJECT_TYPE);
                        String title = visMap.get("title");
                        visBuilder.append(String.format("%s,%s\n", title, id));
                    });

                    listener.onResponse((T) visBuilder.toString());
                } else {
                    listener.onResponse((T) "No Visualization found");
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (ExceptionsHelper.unwrapCause(e) instanceof IndexNotFoundException) {
                    listener.onResponse((T) "No Visualization found");
                } else {
                    listener.onFailure(e);
                }
            }
        });
    }

    private String getIndex(Map<String, String> toolSpec) {
        return toolSpec.get("index") == null ? ".kibana" : (String) toolSpec.get("index");
    }

    @VisibleForTesting
    String trimIdPrefix(String id) {
        id = Optional.ofNullable(id).orElse("");
        if (id.startsWith(SAVED_OBJECT_TYPE)) {
            String prefix = String.format("%s:", SAVED_OBJECT_TYPE);
            return id.substring(prefix.length());
        }
        return id;
    }

    @Override
    public boolean validate(Map<String, String> toolSpec, Map<String, String> parameters) {
        return parameters.containsKey("input") && !Strings.isNullOrEmpty(parameters.get("input"));
    }

}

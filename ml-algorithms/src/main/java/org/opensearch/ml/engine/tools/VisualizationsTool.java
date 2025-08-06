/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.opensearch.ExceptionsHelper;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.Requests;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ToolAnnotation(VisualizationsTool.TYPE)
public class VisualizationsTool implements Tool {
    public static final String NAME = "FindVisualizations";
    public static final String TYPE = "VisualizationTool";
    public static final String VERSION = "v1.0";

    public static final String SAVED_OBJECT_TYPE = "visualization";
    public static final String STRICT_FIELD = "strict";

    /**
     * default number of visualizations returned
     */
    private static final int DEFAULT_SIZE = 3;
    private static final String DEFAULT_DESCRIPTION =
        "Searches for saved visualizations by name. Required: 'input' (visualization name, supports partial matches). Returns: list of matching visualizations with their titles and IDs.";
    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\","
        + "\"properties\":{\"input\":{\"type\":\"string\",\"description\":\"Visualization name to search for\"}},"
        + "\"required\":[\"input\"],"
        + "\"additionalProperties\":false}";
    public static final Map<String, Object> DEFAULT_ATTRIBUTES = Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, false);
    @Setter
    @Getter
    private String description = DEFAULT_DESCRIPTION;

    @Getter
    @Setter
    private String name = NAME;
    @Getter
    @Setter
    private Map<String, Object> attributes;
    @Getter
    @Setter
    private String type = TYPE;
    @Getter
    private final String version = VERSION;
    private final Client client;
    @Getter
    private final String index;
    @Getter
    private final int size;

    @Builder
    public VisualizationsTool(Client client, String index, int size) {
        this.client = client;
        this.index = index;
        this.size = size;

        this.attributes = new HashMap<>();
        attributes.put(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA);
        attributes.put(STRICT_FIELD, true);
    }

    @Override
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> listener) {
        try {
            Map<String, String> parameters = ToolUtils.extractInputParameters(originalParameters, attributes);
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.must().add(QueryBuilders.termQuery("type", SAVED_OBJECT_TYPE));
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(SAVED_OBJECT_TYPE + ".title", parameters.get("input")));

            SearchSourceBuilder searchSourceBuilder = SearchSourceBuilder.searchSource().query(boolQueryBuilder);
            searchSourceBuilder.from(0).size(size);
            SearchRequest searchRequest = Requests.searchRequest(index).source(searchSourceBuilder);

            client.search(searchRequest, new ActionListener<>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    SearchHits hits = searchResponse.getHits();
                    StringBuilder visBuilder = new StringBuilder();
                    visBuilder.append("Title,Id\n");
                    if (hits.getTotalHits().value() > 0) {
                        Arrays.stream(hits.getHits()).forEach(h -> {
                            String id = trimIdPrefix(h.getId());
                            Map<String, String> visMap = (Map<String, String>) h.getSourceAsMap().get(SAVED_OBJECT_TYPE);
                            String title = visMap.get("title");
                            visBuilder.append(String.format(Locale.ROOT, "%s,%s\n", title, id));
                        });

                        listener.onResponse((T) visBuilder.toString());
                    } else {
                        listener.onResponse((T) "No Visualization found");
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    if (ExceptionsHelper.unwrapCause(e) instanceof IndexNotFoundException
                        || ExceptionsHelper.unwrap(e, IndexNotFoundException.class) != null) {
                        listener.onResponse((T) "No Visualization found");
                    } else {
                        listener.onFailure(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to run VisualizationsTool", e);
            listener.onFailure(e);
        }
    }

    String trimIdPrefix(String id) {
        id = Optional.ofNullable(id).orElse("");
        if (id.startsWith(SAVED_OBJECT_TYPE)) {
            String prefix = String.format(Locale.ROOT, "%s:", SAVED_OBJECT_TYPE);
            return id.substring(prefix.length());
        }
        return id;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return parameters != null
            && !parameters.isEmpty()
            && !Strings.isNullOrEmpty(parameters.get("input"))
            && parameters.containsKey("input");
    }

    public static class Factory implements Tool.Factory<VisualizationsTool> {
        private Client client;

        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (VisualizationsTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init(Client client) {
            this.client = client;
        }

        @Override
        public VisualizationsTool create(Map<String, Object> params) {
            String index = params.get("index") == null ? ".kibana" : (String) params.get("index");
            String sizeStr = params.get("size") == null ? "3" : (String) params.get("size");
            int size;
            try {
                size = Integer.parseInt(sizeStr);
            } catch (NumberFormatException ignored) {
                size = DEFAULT_SIZE;
            }
            return VisualizationsTool.builder().client(client).index(index).size(size).build();
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }

        @Override
        public String getDefaultType() {
            return TYPE;
        }

        @Override
        public String getDefaultVersion() {
            return null;
        }

        @Override
        public Map<String, Object> getDefaultAttributes() {
            return DEFAULT_ATTRIBUTES;
        }
    }
}

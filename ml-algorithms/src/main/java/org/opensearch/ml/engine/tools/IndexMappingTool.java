/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.action.support.clustermanager.ClusterManagerNodeRequest.DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.action.admin.indices.get.GetIndexRequest;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.Setter;

@ToolAnnotation(IndexMappingTool.TYPE)
public class IndexMappingTool implements Tool {
    public static final String TYPE = "IndexMappingTool";
    public static final String STRICT_FIELD = "strict";
    private static final String DEFAULT_DESCRIPTION = String
        .join(
            " ",
            "This tool gets index mapping information from a certain index.",
            "It takes 1 required argument named 'index' which is a comma-delimited list of one or more indices to get mapping information from, which expands wildcards.",
            "It takes 1 optional argument named 'local' which means whether to return information from the local node only instead of the cluster manager node (Default is false).",
            "The tool returns a list of index mappings and settings for each index.",
            "The mappings are in JSON format under the key 'properties' which includes the field name as a key and a JSON object with field type under the key 'type'.",
            "The settings are in flattened map with 'index' as the top element and key-value pairs for each setting."
        );
    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\",\""
        + "properties\":{\"index\":{\"type\":\"array\",\"description\":\"OpenSearch index name list, separated by comma. "
        + "for example: [\\\"index1\\\", \\\"index2\\\"]\","
        + "\"items\":{\"type\":\"string\"}}},"
        + "\"required\":[\"index\"],"
        + "\"additionalProperties\":false}";
    public static final Map<String, Object> DEFAULT_ATTRIBUTES = Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, true);

    @Setter
    @Getter
    private String name = IndexMappingTool.TYPE;
    @Getter
    @Setter
    private Map<String, Object> attributes;
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    @Getter
    private String version;

    private Client client;
    @Setter
    private Parser<?, ?> inputParser;
    @Setter
    private Parser<?, ?> outputParser;

    public IndexMappingTool(Client client) {
        this.client = client;

        this.attributes = new HashMap<>();
        attributes.put(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA);
        attributes.put(STRICT_FIELD, true);

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
        try {
            List<String> indexList = new ArrayList<>();
            if (StringUtils.isNotBlank(parameters.get("index"))) {
                try {
                    indexList = gson.fromJson(parameters.get("index"), List.class);
                } catch (Exception e) {
                    indexList.add(parameters.get("index"));
                }
            }

            if (indexList.isEmpty()) {
                @SuppressWarnings("unchecked")
                T empty = (T) ("There were no results searching the index parameter [" + parameters.get("index") + "].");
                listener.onResponse(empty);
                return;
            }

            final String[] indices = indexList.toArray(Strings.EMPTY_ARRAY);

            final IndicesOptions indicesOptions = IndicesOptions.strictExpand();
            final boolean local = Boolean.parseBoolean(parameters.getOrDefault("local", "false"));
            final TimeValue clusterManagerNodeTimeout = DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT;

            ActionListener<GetIndexResponse> internalListener = new ActionListener<GetIndexResponse>() {

                @Override
                public void onResponse(GetIndexResponse getIndexResponse) {
                    try {
                        // Handle empty response
                        if (getIndexResponse.indices().length == 0) {
                            @SuppressWarnings("unchecked")
                            T empty = (T) ("There were no results searching the index parameter [" + parameters.get("index") + "].");
                            listener.onResponse(empty);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        for (String index : getIndexResponse.indices()) {
                            sb.append("index: ").append(index).append("\n\n");

                            MappingMetadata mapping = getIndexResponse.mappings().get(index);
                            if (mapping != null) {
                                sb.append("mappings:\n");
                                for (Entry<String, Object> entry : mapping.sourceAsMap().entrySet()) {
                                    sb.append(entry.getKey()).append("=").append(entry.getValue()).append('\n');
                                }
                                sb.append("\n\n");
                            }

                            Settings settings = getIndexResponse.settings().get(index);
                            if (settings != null) {
                                sb.append("settings:\n").append(settings.toDelimitedString('\n')).append("\n\n");
                            }
                        }

                        @SuppressWarnings("unchecked")
                        T response = (T) sb.toString();
                        listener.onResponse(response);
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(final Exception e) {
                    listener.onFailure(e);
                }

            };
            final GetIndexRequest getIndexRequest = new GetIndexRequest()
                .indices(indices)
                .indicesOptions(indicesOptions)
                .local(local)
                .clusterManagerNodeTimeout(clusterManagerNodeTimeout);

            client.admin().indices().getIndex(getIndexRequest, internalListener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return parameters != null && parameters.containsKey("index");
    }

    /**
     * Factory for the {@link IndexMappingTool}
     */
    public static class Factory implements Tool.Factory<IndexMappingTool> {
        private Client client;

        private static Factory INSTANCE;

        /** 
         * Create or return the singleton factory instance
         */
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (IndexMappingTool.class) {
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
        public IndexMappingTool create(Map<String, Object> map) {
            return new IndexMappingTool(client);
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

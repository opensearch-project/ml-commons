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
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ToolAnnotation(IndexMappingTool.TYPE)
public class IndexMappingTool implements Tool {
    public static final String TYPE = "IndexMappingTool";
    public static final String STRICT_FIELD = "strict";
    private static final String DEFAULT_DESCRIPTION = String
        .join(
            " ",
            "This tool returns index mappings and settings for specified indices.",
            "Required argument: 'index' - comma-delimited list of one or more indices (supports wildcards like 'my-index-*').",
            "Optional argument: 'local' - if true, returns info from local node only instead of cluster manager (default: false).",
            "Response format: For each index, 'mappings' contains field definitions under 'properties' (each field has a 'type'), and 'settings' contains configuration as a flattened key-value map."
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
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> listener) {
        try {
            Map<String, String> parameters = ToolUtils.extractInputParameters(originalParameters, attributes);
            List<String> indexList = new ArrayList<>();
            if (StringUtils.isNotBlank(parameters.get("index"))) {
                try {
                    indexList = gson.fromJson(parameters.get("index"), List.class);
                } catch (Exception e) {
                    // sometimes the input comes from LLM is not a json string, it might a single value of index name
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
            log.error("Failed to run IndexMappingTool", e);
            listener.onFailure(e);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return parameters != null && !parameters.isEmpty() && parameters.containsKey("index");
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

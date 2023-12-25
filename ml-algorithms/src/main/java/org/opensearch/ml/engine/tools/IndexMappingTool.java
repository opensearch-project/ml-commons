/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.action.support.clustermanager.ClusterManagerNodeRequest.DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.util.Strings;
import org.opensearch.action.admin.indices.get.GetIndexRequest;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;

@ToolAnnotation(IndexMappingTool.TYPE)
public class IndexMappingTool extends AbstractTool {
    public static final String TYPE = "IndexMappingTool";

    private static final String DEFAULT_DESCRIPTION = "Use this tool to get index mapping information.";
    private Client client;

    public IndexMappingTool(Client client) {
        super(TYPE, DEFAULT_DESCRIPTION);
        this.client = client;

        this.setOutputParser((Parser<Object, Object>) o -> {
            @SuppressWarnings("unchecked")
            List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
            return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
        });
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        @SuppressWarnings("unchecked")
        List<String> indexList = parameters.containsKey("index")
            ? gson.fromJson(parameters.get("index"), List.class)
            : Collections.emptyList();
        if (indexList.isEmpty()) {
            @SuppressWarnings("unchecked")
            T empty = (T) ("There were no results searching the index parameter [" + parameters.get("index") + "].");
            listener.onResponse(empty);
            return;
        }

        final String[] indices = indexList.toArray(Strings.EMPTY_ARRAY);

        final IndicesOptions indicesOptions = IndicesOptions.strictExpand();
        final boolean local = parameters.containsKey("local") ? Boolean.parseBoolean("local") : false;
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
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        return true;
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
    }
}

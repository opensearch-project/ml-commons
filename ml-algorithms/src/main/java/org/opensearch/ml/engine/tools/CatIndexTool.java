/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.action.admin.indices.stats.CommonStats;
import org.opensearch.action.admin.indices.stats.IndexStats;
import org.opensearch.action.admin.indices.stats.IndicesStatsRequest;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.client.Client;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.opensearch.action.support.clustermanager.ClusterManagerNodeRequest.DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT;
import static org.opensearch.ml.common.utils.StringUtils.gson;

@Log4j2
@ToolAnnotation(CatIndexTool.NAME)
public class CatIndexTool implements Tool {
    public static final String NAME = "CatIndexTool";

    @Setter @Getter
    private String alias;
    private static String DEFAULT_DESCRIPTION = "Use this tool to get index information.";
    @Getter @Setter
    private String description = DEFAULT_DESCRIPTION;
    private Client client;
    private String modelId;
    @Setter
    private Parser<?, ?> inputParser;
    @Setter
    private Parser<?, ?> outputParser;
    private ClusterService clusterService;

    public CatIndexTool(Client client, ClusterService clusterService, String modelId) {
        this.client = client;
        this.clusterService = clusterService;
        this.modelId = modelId;

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
        String[] indices = null;
        if (parameters.containsKey("indices")) {
            @SuppressWarnings("unchecked")
            List<String> indexList = gson.fromJson(parameters.get("indices"), List.class);
            indices = indexList.toArray(new String[0]);
        }
        final IndicesOptions indicesOptions = IndicesOptions.lenientExpandHidden();
        final boolean includeUnloadedSegments = parameters.containsKey("include_unloaded_segments")
            ? Boolean.parseBoolean(parameters.get("include_unloaded_segments"))
            : false;

        final IndicesStatsRequest request = new IndicesStatsRequest().indices(indices)
            .indicesOptions(indicesOptions)
            .all()
            .includeUnloadedSegments(includeUnloadedSegments);

        client.admin().indices().stats(request, ActionListener.wrap(r -> {
            Set<String> indexSet = r.getIndices().keySet();
            // Handle empty set
            if (indexSet.isEmpty()) {
                @SuppressWarnings("unchecked")
                T empty = (T) ("There were no results searching the indices parameter [" + parameters.get("indices") + "].");
                listener.onResponse(empty);
                return;
            }
            
            // Iterate indices in response and map index to stats
            Map<String, IndexState> indexStateMap = new HashMap<>();
            Metadata metadata = clusterService.state().metadata();

            for (String index : indexSet) {
                IndexMetadata indexMetadata = metadata.index(index);
                IndexStats indexStats = r.getIndices().get(index);
                CommonStats totalStats = indexStats.getTotal();
                CommonStats primaryStats = indexStats.getPrimaries();
                IndexState.IndexStateBuilder indexStateBuilder = IndexState.builder();
                indexStateBuilder.status(indexMetadata.getState().toString());
                indexStateBuilder.index(indexStats.getIndex());
                indexStateBuilder.uuid(indexMetadata.getIndexUUID());
                indexStateBuilder.primaryShard(indexMetadata.getNumberOfShards());
                indexStateBuilder.replicaShard(indexMetadata.getNumberOfReplicas());
                indexStateBuilder.docCount(primaryStats.docs.getCount());
                indexStateBuilder.docDeleted(primaryStats.docs.getDeleted());
                indexStateBuilder.storeSize(totalStats.getStore().size().toString());
                indexStateBuilder.primaryStoreSize(primaryStats.getStore().getSize().toString());
                indexStateMap.put(index, indexStateBuilder.build());
            }

            // Get cluster health for each index
            final ClusterHealthRequest clusterHealthRequest = new ClusterHealthRequest(indexSet.toArray(new String[0]))
                .indicesOptions(indicesOptions)
                .local(parameters.containsKey("local") ? Boolean.parseBoolean("local") : false)
                .clusterManagerNodeTimeout(DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT);

            client.admin().cluster().health(clusterHealthRequest, ActionListener.wrap(res -> {
                // Add health to index stats
                Map<String, ClusterIndexHealth> indexHealthMap = res.getIndices();
                for (String index : indexHealthMap.keySet()) {
                    IndexStats indexStats = r.getIndices().get(index);
                    final ClusterIndexHealth indexHealth = indexHealthMap.get(index);
                    final String health;
                    if (indexHealth != null) {
                        health = indexHealth.getStatus().toString().toLowerCase(Locale.ROOT);
                    } else if (indexStats != null) {
                        health = "red*";
                    } else {
                        health = "";
                    }
                    indexStateMap.get(index).setHealth(health);
                }
                // Prepare output with header row
                StringBuilder responseBuilder = new StringBuilder(
                    "health\tstatus\tindex\tuuid\tpri\trep\tdocs.count\tdocs.deleted\tstore.size\tpri.store.size\n"
                );
                // Output a row for each index
                for (IndexState state : indexStateMap.values()) {
                    responseBuilder.append(state.getHealth()).append('\t');
                    responseBuilder.append(state.getStatus()).append('\t');
                    responseBuilder.append(state.getIndex()).append('\t');
                    responseBuilder.append(state.getUuid()).append('\t');
                    responseBuilder.append(state.getPrimaryShard()).append('\t');
                    responseBuilder.append(state.getReplicaShard()).append('\t');
                    responseBuilder.append(state.getDocCount()).append('\t');
                    responseBuilder.append(state.getDocDeleted()).append('\t');
                    responseBuilder.append(state.getStoreSize()).append('\t');
                    responseBuilder.append(state.getPrimaryStoreSize()).append('\n');
                }
                @SuppressWarnings("unchecked")
                T s = (T) responseBuilder.toString();
                listener.onResponse(s);
            }, ex -> { listener.onFailure(ex); }));
        }, e -> { listener.onFailure(e); }));
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Data
    public static class IndexState {
        private String health;
        private String status;
        private String index;
        private String uuid;
        private Integer primaryShard;
        private Integer replicaShard;
        private Long docCount;
        private Long docDeleted;
        private String storeSize;
        private String primaryStoreSize;

        @Builder
        public IndexState(String health, String status, String index, String uuid, Integer primaryShard, Integer replicaShard, Long docCount, Long docDeleted, String storeSize, String primaryStoreSize) {
            this.health = health;
            this.status = status;
            this.index = index;
            this.uuid = uuid;
            this.primaryShard = primaryShard;
            this.replicaShard = replicaShard;
            this.docCount = docCount;
            this.docDeleted = docDeleted;
            this.storeSize = storeSize;
            this.primaryStoreSize = primaryStoreSize;
        }
    }


    @Override
    public String getName() {
        return CatIndexTool.NAME;
    }

    @Override
    public void setName(String s) {

    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        return true;
    }

    public static class Factory implements Tool.Factory<CatIndexTool> {
        private Client client;
        private ClusterService clusterService;

        private static Factory INSTANCE;
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (CatIndexTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init(Client client, ClusterService clusterService) {
            this.client = client;
            this.clusterService = clusterService;
        }

        @Override
        public CatIndexTool create(Map<String, Object> map) {
            return new CatIndexTool(client, clusterService, (String) map.get("model_id"));
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }
}
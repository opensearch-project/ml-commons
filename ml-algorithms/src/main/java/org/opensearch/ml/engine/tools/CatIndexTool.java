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
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;

import java.io.IOException;
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
    private Parser inputParser;
    @Setter
    private Parser outputParser;
    private ClusterService clusterService;

    public CatIndexTool(Client client, ClusterService clusterService, String modelId) {
        this.client = client;
        this.clusterService = clusterService;
        this.modelId = modelId;

        outputParser = new Parser() {
            @Override
            public Object parse(Object o) {
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        };
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        List<String> indexList = gson.fromJson(parameters.get("indices"), List.class);
        String[] indices = parameters.containsKey("indices")? indexList.toArray(new String[0]) : new String[]{};

        final IndicesOptions indicesOptions = IndicesOptions.lenientExpandHidden();

        final IndicesStatsRequest request = new IndicesStatsRequest();
        request.indices(indices);
        request.indicesOptions(indicesOptions);
        request.all();
        boolean includeUnloadedSegments = parameters.containsKey("include_unloaded_segments")? Boolean.parseBoolean(parameters.get("include_unloaded_segments")) : false;
        request.includeUnloadedSegments(includeUnloadedSegments);

        client.admin().indices().stats(request, ActionListener.wrap(r -> {
            try {
                Set<String> indexSet = r.getIndices().keySet(); //TODO: handle empty case
                XContentBuilder xContentBuilder = XContentBuilder.builder(XContentType.JSON.xContent());
                r.toXContent(xContentBuilder, ToXContent.EMPTY_PARAMS);
                String response = xContentBuilder.toString();

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

                final ClusterHealthRequest clusterHealthRequest = new ClusterHealthRequest();
                clusterHealthRequest.indices(indexSet.toArray(new String[0]));
                clusterHealthRequest.indicesOptions(indicesOptions);
                boolean local = parameters.containsKey("local")? Boolean.parseBoolean("local") : false;
                clusterHealthRequest.local(local);
                clusterHealthRequest.clusterManagerNodeTimeout(DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT);

                client.admin().cluster().health(clusterHealthRequest, ActionListener.wrap(res-> {
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
                    StringBuilder responseBuilder = new StringBuilder("health\tstatus\tindex\tuuid\tpri\trep\tdocs.count\tdocs.deleted\tstore.size\tpri.store.size\n");
                    for (String index : indexStateMap.keySet()) {
                        responseBuilder.append(indexStateMap.get(index).toString()).append("\n");
                    }
                    listener.onResponse((T)responseBuilder.toString());
                }, ex->{listener.onFailure(ex);}));
            } catch (IOException e) {
                listener.onFailure(e);
            }
        }, e -> {
            listener.onFailure(e);
        }));
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

        @Override
        public String toString() {
            return
                    health + '\t' +
                    status + '\t' +
                    index + '\t' +
                    uuid + '\t' +
                    primaryShard + '\t' +
                    replicaShard + '\t' +
                    docCount + '\t' +
                    docDeleted + '\t' +
                    storeSize + '\t' +
                    primaryStoreSize;
        }
    }


    @Override
    public String getName() {
        return CatIndexTool.NAME;
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
            return new CatIndexTool(client, clusterService, (String)map.get("model_id"));
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }
}
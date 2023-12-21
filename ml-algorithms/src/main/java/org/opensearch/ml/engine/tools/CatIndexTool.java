/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.action.support.clustermanager.ClusterManagerNodeRequest.DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.util.Strings;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.admin.cluster.state.ClusterStateRequest;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.opensearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.opensearch.action.admin.indices.stats.CommonStats;
import org.opensearch.action.admin.indices.stats.IndexStats;
import org.opensearch.action.admin.indices.stats.IndicesStatsRequest;
import org.opensearch.action.admin.indices.stats.IndicesStatsResponse;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.client.Client;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Table;
import org.opensearch.common.Table.Cell;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.index.IndexSettings;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;

@ToolAnnotation(CatIndexTool.TYPE)
public class CatIndexTool extends AbstractTool {
    public static final String TYPE = "CatIndexTool";
    private static final String DEFAULT_DESCRIPTION = "Use this tool to get index information.";

    private Client client;
    @SuppressWarnings("unused")
    private ClusterService clusterService;

    public CatIndexTool(Client client, ClusterService clusterService) {
        super(TYPE, DEFAULT_DESCRIPTION);
        this.client = client;
        this.clusterService = clusterService;

        this.setOutputParser((Parser<Object, Object>) parser -> {
            @SuppressWarnings("unchecked")
            List<ModelTensors> mlModelOutputs = (List<ModelTensors>) parser;
            return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
        });
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        // TODO: This logic exactly matches the OpenSearch _cat/indices REST action. If code at
        // o.o.rest/action/cat/RestIndicesAction.java changes those changes need to be reflected here
        // https://github.com/opensearch-project/ml-commons/pull/1582#issuecomment-1796962876
        @SuppressWarnings("unchecked")
        List<String> indexList = parameters.containsKey("indices")
            ? gson.fromJson(parameters.get("indices"), List.class)
            : Collections.emptyList();
        final String[] indices = indexList.toArray(Strings.EMPTY_ARRAY);

        final IndicesOptions indicesOptions = IndicesOptions.strictExpand();
        final boolean local = parameters.containsKey("local") ? Boolean.parseBoolean("local") : false;
        final TimeValue clusterManagerNodeTimeout = DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT;
        final boolean includeUnloadedSegments = parameters.containsKey("include_unloaded_segments")
            ? Boolean.parseBoolean(parameters.get("include_unloaded_segments"))
            : false;

        final ActionListener<Table> internalListener = ActionListener.notifyOnce(ActionListener.wrap(table -> {
            // Handle empty table
            if (table.getRows().isEmpty()) {
                @SuppressWarnings("unchecked")
                T empty = (T) ("There were no results searching the indices parameter [" + parameters.get("indices") + "].");
                listener.onResponse(empty);
                return;
            }
            StringBuilder sb = new StringBuilder(
                // Currently using c.value which is short header matching _cat/indices
                // May prefer to use c.attr.get("desc") for full description
                table.getHeaders().stream().map(c -> c.value.toString()).collect(Collectors.joining("\t", "", "\n"))
            );
            for (List<Cell> row : table.getRows()) {
                sb.append(row.stream().map(c -> c.value == null ? null : c.value.toString()).collect(Collectors.joining("\t", "", "\n")));
            }
            @SuppressWarnings("unchecked")
            T response = (T) sb.toString();
            listener.onResponse(response);
        }, listener::onFailure));

        sendGetSettingsRequest(
            indices,
            indicesOptions,
            local,
            clusterManagerNodeTimeout,
            client,
            new ActionListener<GetSettingsResponse>() {
                @Override
                public void onResponse(final GetSettingsResponse getSettingsResponse) {
                    final GroupedActionListener<ActionResponse> groupedListener = createGroupedListener(4, internalListener);
                    groupedListener.onResponse(getSettingsResponse);

                    // The list of indices that will be returned is determined by the indices returned from the Get Settings call.
                    // All the other requests just provide additional detail, and wildcards may be resolved differently depending on the
                    // type of request in the presence of security plugins (looking at you, ClusterHealthRequest), so
                    // force the IndicesOptions for all the sub-requests to be as inclusive as possible.
                    final IndicesOptions subRequestIndicesOptions = IndicesOptions.lenientExpandHidden();

                    sendIndicesStatsRequest(
                        indices,
                        subRequestIndicesOptions,
                        includeUnloadedSegments,
                        client,
                        ActionListener.wrap(groupedListener::onResponse, groupedListener::onFailure)
                    );
                    sendClusterStateRequest(
                        indices,
                        subRequestIndicesOptions,
                        local,
                        clusterManagerNodeTimeout,
                        client,
                        ActionListener.wrap(groupedListener::onResponse, groupedListener::onFailure)
                    );
                    sendClusterHealthRequest(
                        indices,
                        subRequestIndicesOptions,
                        local,
                        clusterManagerNodeTimeout,
                        client,
                        ActionListener.wrap(groupedListener::onResponse, groupedListener::onFailure)
                    );
                }

                @Override
                public void onFailure(final Exception e) {
                    internalListener.onFailure(e);
                }
            }
        );
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * We're using the Get Settings API here to resolve the authorized indices for the user.
     * This is because the Cluster State and Cluster Health APIs do not filter output based
     * on index privileges, so they can't be used to determine which indices are authorized
     * or not. On top of this, the Indices Stats API cannot be used either to resolve indices
     * as it does not provide information for all existing indices (for example recovering
     * indices or non replicated closed indices are not reported in indices stats response).
     */
    private void sendGetSettingsRequest(
        final String[] indices,
        final IndicesOptions indicesOptions,
        final boolean local,
        final TimeValue clusterManagerNodeTimeout,
        final Client client,
        final ActionListener<GetSettingsResponse> listener
    ) {
        final GetSettingsRequest request = new GetSettingsRequest();
        request.indices(indices);
        request.indicesOptions(indicesOptions);
        request.local(local);
        request.clusterManagerNodeTimeout(clusterManagerNodeTimeout);
        request.names(IndexSettings.INDEX_SEARCH_THROTTLED.getKey());

        client.admin().indices().getSettings(request, listener);
    }

    private void sendClusterStateRequest(
        final String[] indices,
        final IndicesOptions indicesOptions,
        final boolean local,
        final TimeValue clusterManagerNodeTimeout,
        final Client client,
        final ActionListener<ClusterStateResponse> listener
    ) {

        final ClusterStateRequest request = new ClusterStateRequest();
        request.indices(indices);
        request.indicesOptions(indicesOptions);
        request.local(local);
        request.clusterManagerNodeTimeout(clusterManagerNodeTimeout);

        client.admin().cluster().state(request, listener);
    }

    private void sendClusterHealthRequest(
        final String[] indices,
        final IndicesOptions indicesOptions,
        final boolean local,
        final TimeValue clusterManagerNodeTimeout,
        final Client client,
        final ActionListener<ClusterHealthResponse> listener
    ) {

        final ClusterHealthRequest request = new ClusterHealthRequest();
        request.indices(indices);
        request.indicesOptions(indicesOptions);
        request.local(local);
        request.clusterManagerNodeTimeout(clusterManagerNodeTimeout);

        client.admin().cluster().health(request, listener);
    }

    private void sendIndicesStatsRequest(
        final String[] indices,
        final IndicesOptions indicesOptions,
        final boolean includeUnloadedSegments,
        final Client client,
        final ActionListener<IndicesStatsResponse> listener
    ) {

        final IndicesStatsRequest request = new IndicesStatsRequest();
        request.indices(indices);
        request.indicesOptions(indicesOptions);
        request.all();
        request.includeUnloadedSegments(includeUnloadedSegments);

        client.admin().indices().stats(request, listener);
    }

    private GroupedActionListener<ActionResponse> createGroupedListener(final int size, final ActionListener<Table> listener) {
        return new GroupedActionListener<>(new ActionListener<Collection<ActionResponse>>() {
            @Override
            public void onResponse(final Collection<ActionResponse> responses) {
                try {
                    GetSettingsResponse settingsResponse = extractResponse(responses, GetSettingsResponse.class);
                    Map<String, Settings> indicesSettings = StreamSupport
                        .stream(Spliterators.spliterator(settingsResponse.getIndexToSettings().entrySet(), 0), false)
                        .collect(Collectors.toMap(cursor -> cursor.getKey(), cursor -> cursor.getValue()));

                    ClusterStateResponse stateResponse = extractResponse(responses, ClusterStateResponse.class);
                    Map<String, IndexMetadata> indicesStates = StreamSupport
                        .stream(stateResponse.getState().getMetadata().spliterator(), false)
                        .collect(Collectors.toMap(indexMetadata -> indexMetadata.getIndex().getName(), Function.identity()));

                    ClusterHealthResponse healthResponse = extractResponse(responses, ClusterHealthResponse.class);
                    Map<String, ClusterIndexHealth> indicesHealths = healthResponse.getIndices();

                    IndicesStatsResponse statsResponse = extractResponse(responses, IndicesStatsResponse.class);
                    Map<String, IndexStats> indicesStats = statsResponse.getIndices();

                    Table responseTable = buildTable(indicesSettings, indicesHealths, indicesStats, indicesStates);
                    listener.onResponse(responseTable);
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(final Exception e) {
                listener.onFailure(e);
            }
        }, size);
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        return true;
    }

    /**
     * Factory for the {@link CatIndexTool}
     */
    public static class Factory implements Tool.Factory<CatIndexTool> {
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
            synchronized (CatIndexTool.class) {
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
        public CatIndexTool create(Map<String, Object> map) {
            return new CatIndexTool(client, clusterService);
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }

    private Table getTableWithHeader() {
        Table table = new Table();
        table.startHeaders();
        // First param is cell.value which is currently returned
        // Second param is cell.attr we may want to use attr.desc in the future
        table.addCell("health", "alias:h;desc:current health status");
        table.addCell("status", "alias:s;desc:open/close status");
        table.addCell("index", "alias:i,idx;desc:index name");
        table.addCell("uuid", "alias:id,uuid;desc:index uuid");
        table.addCell("pri", "alias:p,shards.primary,shardsPrimary;text-align:right;desc:number of primary shards");
        table.addCell("rep", "alias:r,shards.replica,shardsReplica;text-align:right;desc:number of replica shards");
        table.addCell("docs.count", "alias:dc,docsCount;text-align:right;desc:available docs");
        table.addCell("docs.deleted", "alias:dd,docsDeleted;text-align:right;desc:deleted docs");
        table.addCell("store.size", "sibling:pri;alias:ss,storeSize;text-align:right;desc:store size of primaries & replicas");
        table.addCell("pri.store.size", "text-align:right;desc:store size of primaries");
        // Above includes all the default fields for cat indices. See RestIndicesAction for a lot more that could be included.
        table.endHeaders();
        return table;
    }

    private Table buildTable(
        final Map<String, Settings> indicesSettings,
        final Map<String, ClusterIndexHealth> indicesHealths,
        final Map<String, IndexStats> indicesStats,
        final Map<String, IndexMetadata> indicesMetadatas
    ) {
        final Table table = getTableWithHeader();

        indicesSettings.forEach((indexName, settings) -> {
            if (indicesMetadatas.containsKey(indexName) == false) {
                // the index exists in the Get Indices response but is not present in the cluster state:
                // it is likely that the index was deleted in the meanwhile, so we ignore it.
                return;
            }

            final IndexMetadata indexMetadata = indicesMetadatas.get(indexName);
            final IndexMetadata.State indexState = indexMetadata.getState();
            final IndexStats indexStats = indicesStats.get(indexName);

            final String health;
            final ClusterIndexHealth indexHealth = indicesHealths.get(indexName);
            if (indexHealth != null) {
                health = indexHealth.getStatus().toString().toLowerCase(Locale.ROOT);
            } else if (indexStats != null) {
                health = "red*";
            } else {
                health = "";
            }

            final CommonStats primaryStats;
            final CommonStats totalStats;

            if (indexStats == null || indexState == IndexMetadata.State.CLOSE) {
                primaryStats = new CommonStats();
                totalStats = new CommonStats();
            } else {
                primaryStats = indexStats.getPrimaries();
                totalStats = indexStats.getTotal();
            }
            table.startRow();
            table.addCell(health);
            table.addCell(indexState.toString().toLowerCase(Locale.ROOT));
            table.addCell(indexName);
            table.addCell(indexMetadata.getIndexUUID());
            table.addCell(indexHealth == null ? null : indexHealth.getNumberOfShards());
            table.addCell(indexHealth == null ? null : indexHealth.getNumberOfReplicas());

            table.addCell(primaryStats.getDocs() == null ? null : primaryStats.getDocs().getCount());
            table.addCell(primaryStats.getDocs() == null ? null : primaryStats.getDocs().getDeleted());

            table.addCell(totalStats.getStore() == null ? null : totalStats.getStore().size());
            table.addCell(primaryStats.getStore() == null ? null : primaryStats.getStore().size());

            table.endRow();
        });

        return table;
    }

    @SuppressWarnings("unchecked")
    private static <A extends ActionResponse> A extractResponse(final Collection<? extends ActionResponse> responses, Class<A> c) {
        return (A) responses.stream().filter(c::isInstance).findFirst().get();
    }
}

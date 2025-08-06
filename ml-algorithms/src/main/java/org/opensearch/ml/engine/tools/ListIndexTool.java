/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.action.support.clustermanager.ClusterManagerNodeRequest.DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
import org.opensearch.action.pagination.IndexPaginationStrategy;
import org.opensearch.action.pagination.PageParams;
import org.opensearch.action.pagination.PageToken;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Table;
import org.opensearch.common.Table.Cell;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.index.IndexSettings;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ToolAnnotation(ListIndexTool.TYPE)
public class ListIndexTool implements Tool {
    public static final String TYPE = "ListIndexTool";
    public static final String STRICT_FIELD = "strict";
    // This needs to be changed once it's changed in opensearch core in RestIndicesListAction.
    private static final int MAX_SUPPORTED_LIST_INDICES_PAGE_SIZE = 5000;
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final String DEFAULT_DESCRIPTION = String
        .join(
            " ",
            "This tool returns information about indices in the OpenSearch cluster along with the index `health`, `status`, `index`, `uuid`, `pri`, `rep`, `docs.count`, `docs.deleted`, `store.size`, `pri.store. size `, `pri.store.size`, `pri.store`.",
            "Optional arguments: 1. `indices`, a comma-delimited list of one or more indices to get information from (default is an empty list meaning all indices). Use only valid index names.",
            "2. `local`, whether to return information from the local node only instead of the cluster manager node (Default is false)"
        );
    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\","
        + "\"properties\":{\"indices\":{\"type\":\"array\",\"items\": {\"type\": \"string\"},"
        + "\"description\":\"OpenSearch index name list, separated by comma. "
        + "for example: [\\\"index1\\\", \\\"index2\\\"], use empty array [] to list all indices in the cluster\"}},"
        + "\"additionalProperties\":false}";
    public static final Map<String, Object> DEFAULT_ATTRIBUTES = Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, false);

    @Setter
    @Getter
    private String name = ListIndexTool.TYPE;
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    @Getter
    @Setter
    private Map<String, Object> attributes;
    @Getter
    private String version;

    private Client client;
    @Setter
    private Parser<?, ?> inputParser;
    @Setter
    private Parser<?, ?> outputParser;
    @SuppressWarnings("unused")
    private ClusterService clusterService;

    public ListIndexTool(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;

        outputParser = new Parser<>() {
            @Override
            public Object parse(Object o) {
                @SuppressWarnings("unchecked")
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        };

        this.attributes = new HashMap<>();
        attributes.put(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA);
        attributes.put(STRICT_FIELD, false);
    }

    @Override
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> listener) {
        // TODO: This logic exactly matches the OpenSearch _list/indices REST action. If code at
        // o.o.rest/action/list/RestIndicesListAction.java changes those changes need to be reflected here
        try {
            Map<String, String> parameters = ToolUtils.extractInputParameters(originalParameters, attributes);
            List<String> indexList = new ArrayList<>();
            if (StringUtils.isNotBlank(parameters.get("indices"))) {
                indexList = parameters.containsKey("indices")
                    ? gson.fromJson(parameters.get("indices"), List.class)
                    : Collections.emptyList();
            }
            final String[] indices = indexList.toArray(Strings.EMPTY_ARRAY);

            final IndicesOptions indicesOptions = IndicesOptions.strictExpand();
            final boolean local = parameters.containsKey("local") && Boolean.parseBoolean(parameters.get("local"));
            final boolean includeUnloadedSegments = Boolean.parseBoolean(parameters.get("include_unloaded_segments"));
            final int pageSize = parameters.containsKey("page_size")
                ? NumberUtils.toInt(parameters.get("page_size"), DEFAULT_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;
            final PageParams pageParams = new PageParams(null, PageParams.PARAM_ASC_SORT_VALUE, pageSize);

            final ActionListener<Table> internalListener = ActionListener.notifyOnce(ActionListener.wrap(table -> {
                // Handle empty table
                if (table == null || table.getRows().isEmpty()) {
                    @SuppressWarnings("unchecked")
                    T empty = (T) ("There were no results searching the indices parameter [" + parameters.get("indices") + "].");
                    listener.onResponse(empty);
                    return;
                }
                StringBuilder sb = new StringBuilder(
                    // Currently using c.value which is short header matching _cat/indices
                    // May prefer to use c.attr.get("desc") for full description
                    table.getHeaders().stream().map(c -> c.value.toString()).collect(Collectors.joining(",", "", "\n"))
                );
                for (List<Cell> row : table.getRows()) {
                    sb
                        .append(
                            row.stream().map(c -> c.value == null ? null : c.value.toString()).collect(Collectors.joining(",", "", "\n"))
                        );
                }
                @SuppressWarnings("unchecked")
                T response = (T) sb.toString();
                listener.onResponse(response);
            }, listener::onFailure));

            fetchClusterInfoAndPages(
                indices,
                local,
                includeUnloadedSegments,
                pageParams,
                indicesOptions,
                new ConcurrentLinkedQueue<>(),
                internalListener
            );
        } catch (Exception e) {
            log.error("Failed to run ListIndexTool", e);
            listener.onFailure(e);
        }
    }

    private void fetchClusterInfoAndPages(
        String[] indices,
        boolean local,
        boolean includeUnloadedSegments,
        PageParams pageParams,
        IndicesOptions indicesOptions,
        Queue<Collection<ActionResponse>> pageResults,
        ActionListener<Table> originalListener
    ) {
        // First fetch metadata like index setting and cluster states and then fetch index details in batches to save efforts.
        sendGetSettingsRequest(indices, indicesOptions, local, client, new ActionListener<>() {
            @Override
            public void onResponse(final GetSettingsResponse getSettingsResponse) {
                // The list of indices that will be returned is determined by the indices returned from the Get Settings call.
                // All the other requests just provide additional detail, and wildcards may be resolved differently depending on the
                // type of request in the presence of security plugins (looking at you, ClusterHealthRequest), so
                // force the IndicesOptions for all the sub-requests to be as inclusive as possible.
                final IndicesOptions subRequestIndicesOptions = IndicesOptions.lenientExpandHidden();
                // Indices that were successfully resolved during the get settings request might be deleted when the
                // subsequent cluster state, cluster health and indices stats requests execute. We have to distinguish two cases:
                // 1) the deleted index was explicitly passed as parameter to the /_cat/indices request. In this case we
                // want the subsequent requests to fail.
                // 2) the deleted index was resolved as part of a wildcard or _all. In this case, we want the subsequent
                // requests not to fail on the deleted index (as we want to ignore wildcards that cannot be resolved).
                // This behavior can be ensured by letting the cluster state, cluster health and indices stats requests
                // re-resolve the index names with the same indices options that we used for the initial cluster state
                // request (strictExpand).
                sendClusterStateRequest(indices, subRequestIndicesOptions, local, client, new ActionListener<>() {
                    @Override
                    public void onResponse(ClusterStateResponse clusterStateResponse) {
                        // Starts to fetch index details here, if a batch fails build whatever we have and return.
                        fetchPages(
                            indices,
                            local,
                            DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT,
                            includeUnloadedSegments,
                            pageParams,
                            pageResults,
                            clusterStateResponse,
                            getSettingsResponse,
                            subRequestIndicesOptions,
                            originalListener
                        );
                    }

                    @Override
                    public void onFailure(final Exception e) {
                        originalListener.onFailure(e);
                    }
                });
            }

            @Override
            public void onFailure(final Exception e) {
                originalListener.onFailure(e);
            }
        });
    }

    private void fetchPages(
        String[] indices,
        boolean local,
        TimeValue clusterManagerNodeTimeout,
        boolean includeUnloadedSegments,
        PageParams pageParams,
        Queue<Collection<ActionResponse>> pageResults,
        ClusterStateResponse clusterStateResponse,
        GetSettingsResponse getSettingsResponse,
        IndicesOptions subRequestIndicesOptions,
        ActionListener<Table> originalListener
    ) {
        final ActionListener<PageToken> iterativeListener = ActionListener.wrap(r -> {
            // when previous response returns, build next request with response and invoke again.
            PageParams nextPageParams = new PageParams(r.getNextToken(), pageParams.getSort(), pageParams.getSize());
            // when next page doesn't exist or reaches max supported page size, return.
            if (r.getNextToken() == null || pageResults.size() >= MAX_SUPPORTED_LIST_INDICES_PAGE_SIZE) {
                Table table = buildTable(clusterStateResponse, getSettingsResponse, pageResults);
                originalListener.onResponse(table);
            } else {
                fetchPages(
                    indices,
                    local,
                    clusterManagerNodeTimeout,
                    includeUnloadedSegments,
                    nextPageParams,
                    pageResults,
                    clusterStateResponse,
                    getSettingsResponse,
                    subRequestIndicesOptions,
                    originalListener
                );
            }
        }, e -> {
            log.error("Failed to fetch index info for page: {}", pageParams.getRequestedToken());
            // Do not throw the exception, just return whatever we have.
            originalListener.onResponse(buildTable(clusterStateResponse, getSettingsResponse, pageResults));
        });
        IndexPaginationStrategy paginationStrategy = getPaginationStrategy(pageParams, clusterStateResponse);
        // For non-paginated queries, indicesToBeQueried would be same as indices retrieved from
        // rest request and unresolved, while for paginated queries, it would be a list of indices
        // already resolved by ClusterStateRequest and to be displayed in a page.
        final String[] indicesToBeQueried = Objects.isNull(paginationStrategy)
            ? indices
            : paginationStrategy.getRequestedEntities().toArray(new String[0]);
        // After the group listener returns, one page complete and prepare for next page.
        // We put the single page result into the pageResults queue for future buildTable.
        final GroupedActionListener<ActionResponse> groupedListener = createGroupedListener(
            pageResults,
            paginationStrategy.getResponseToken(),
            iterativeListener
        );

        sendIndicesStatsRequest(
            indicesToBeQueried,
            subRequestIndicesOptions,
            includeUnloadedSegments,
            client,
            ActionListener.wrap(groupedListener::onResponse, groupedListener::onFailure)
        );

        sendClusterHealthRequest(
            indicesToBeQueried,
            subRequestIndicesOptions,
            local,
            clusterManagerNodeTimeout,
            client,
            ActionListener.wrap(groupedListener::onResponse, groupedListener::onFailure)
        );
    }

    protected IndexPaginationStrategy getPaginationStrategy(PageParams pageParams, ClusterStateResponse clusterStateResponse) {
        return new IndexPaginationStrategy(pageParams, clusterStateResponse.getState());
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
        final Client client,
        final ActionListener<GetSettingsResponse> listener
    ) {
        final GetSettingsRequest request = new GetSettingsRequest();
        request.indices(indices);
        request.indicesOptions(indicesOptions);
        request.local(local);
        request.clusterManagerNodeTimeout(DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT);
        request.names(IndexSettings.INDEX_SEARCH_THROTTLED.getKey());

        client.admin().indices().getSettings(request, listener);
    }

    private void sendClusterStateRequest(
        final String[] indices,
        final IndicesOptions indicesOptions,
        final boolean local,
        final Client client,
        final ActionListener<ClusterStateResponse> listener
    ) {

        final ClusterStateRequest request = new ClusterStateRequest();
        request.indices(indices);
        request.indicesOptions(indicesOptions);
        request.local(local);
        request.clusterManagerNodeTimeout(DEFAULT_CLUSTER_MANAGER_NODE_TIMEOUT);

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

    // group listener only accept two action response: IndicesStatsResponse and ClusterHealthResponse
    private GroupedActionListener<ActionResponse> createGroupedListener(
        final Queue<Collection<ActionResponse>> pageResults,
        final PageToken pageToken,
        final ActionListener<PageToken> listener
    ) {
        return new GroupedActionListener<>(new ActionListener<>() {
            @Override
            public void onResponse(final Collection<ActionResponse> responses) {
                pageResults.add(responses);
                listener.onResponse(pageToken);
            }

            @Override
            public void onFailure(final Exception e) {
                listener.onFailure(e);
            }
        }, 2);
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return parameters != null && !parameters.isEmpty();
    }

    /**
     * Factory for the {@link ListIndexTool}
     */
    public static class Factory implements Tool.Factory<ListIndexTool> {
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
            synchronized (ListIndexTool.class) {
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
        public ListIndexTool create(Map<String, Object> map) {
            return new ListIndexTool(client, clusterService);
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

    private Table getTableWithHeader() {
        Table table = new Table();
        table.startHeaders();
        // First param is cell.value which is currently returned
        // Second param is cell.attr we may want to use attr.desc in the future
        table.addCell("row", "alias:r;desc:row number");
        table.addCell("health", "alias:h;desc:current health status");
        table.addCell("status", "alias:s;desc:open/close status");
        table.addCell("index", "alias:i,idx;desc:index name");
        table.addCell("uuid", "alias:id,uuid;desc:index uuid");
        table
            .addCell(
                "pri(number of primary shards)",
                "alias:p,shards.primary,shardsPrimary;text-align:right;desc:number of primary shards"
            );
        table
            .addCell(
                "rep(number of replica shards)",
                "alias:r,shards.replica,shardsReplica;text-align:right;desc:number of replica shards"
            );
        table.addCell("docs.count(number of available documents)", "alias:dc,docsCount;text-align:right;desc:available docs");
        table.addCell("docs.deleted(number of deleted documents)", "alias:dd,docsDeleted;text-align:right;desc:deleted docs");
        table
            .addCell(
                "store.size(store size of primary and replica shards)",
                "sibling:pri;alias:ss,storeSize;text-align:right;desc:store size of primaries & replicas"
            );
        table.addCell("pri.store.size(store size of primary shards)", "text-align:right;desc:store size of primaries");
        // Above includes all the default fields for cat indices. See RestIndicesAction for a lot more that could be included.
        table.endHeaders();
        return table;
    }

    private Table buildTable(
        ClusterStateResponse clusterStateResponse,
        GetSettingsResponse getSettingsResponse,
        Queue<Collection<ActionResponse>> responses
    ) {
        if (responses == null || responses.isEmpty() || responses.peek().isEmpty()) {
            return null;
        }
        Tuple<Map<String, IndexStats>, Map<String, ClusterIndexHealth>> tuple = aggregateResults(responses);
        final Table table = getTableWithHeader();
        AtomicInteger rowNum = new AtomicInteger(0);
        Map<String, Settings> indicesSettings = StreamSupport
            .stream(Spliterators.spliterator(getSettingsResponse.getIndexToSettings().entrySet(), 0), false)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, IndexMetadata> indicesStates = StreamSupport
            .stream(clusterStateResponse.getState().getMetadata().spliterator(), false)
            .collect(Collectors.toMap(indexMetadata -> indexMetadata.getIndex().getName(), Function.identity()));

        Map<String, ClusterIndexHealth> indicesHealths = tuple.v2();
        Map<String, IndexStats> indicesStats = tuple.v1();
        indicesSettings.forEach((indexName, settings) -> {
            if (!indicesStates.containsKey(indexName)) {
                // the index exists in the Get Indices response but is not present in the cluster state:
                // it is likely that the index was deleted in the meanwhile, so we ignore it.
                return;
            }

            final IndexMetadata indexMetadata = indicesStates.get(indexName);
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
            table.addCell(rowNum.addAndGet(1));
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

    private Tuple<Map<String, IndexStats>, Map<String, ClusterIndexHealth>> aggregateResults(Queue<Collection<ActionResponse>> responses) {
        // Each batch produces a collection of action response, aggregate them together to build table easier.
        Map<String, IndexStats> indexStatsMap = new HashMap<>();
        Map<String, ClusterIndexHealth> clusterIndexHealthMap = new HashMap<>();
        for (Collection<ActionResponse> response : responses) {
            if (response != null && !response.isEmpty()) {
                response.forEach(x -> {
                    if (x instanceof IndicesStatsResponse) {
                        indexStatsMap.putAll(((IndicesStatsResponse) x).getIndices());
                    } else if (x instanceof ClusterHealthResponse) {
                        clusterIndexHealthMap.putAll(((ClusterHealthResponse) x).getIndices());
                    } else {
                        throw new IllegalStateException("Unexpected action response type: " + x.getClass().getName());
                    }
                });
            }
        }
        return new Tuple<>(indexStatsMap, clusterIndexHealthMap);
    }
}

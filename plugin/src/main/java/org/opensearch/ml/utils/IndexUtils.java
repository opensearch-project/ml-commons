/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import java.util.List;
import java.util.Locale;

import org.opensearch.action.ActionListener;
import org.opensearch.action.admin.indices.stats.IndicesStatsRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;

public class IndexUtils {
    /**
     * Status string of index that does not exist
     */
    public static final String NONEXISTENT_INDEX_STATUS = "non-existent";

    /**
     * Status string when an alias exists, but does not point to an index
     */
    public static final String ALIAS_EXISTS_NO_INDICES_STATUS = "alias exists, but does not point to any indices";

    private Client client;
    private ClusterService clusterService;

    /**
     * Inject annotation required by Guice to instantiate EntityResultTransportAction (transitive dependency)
     *
     * @param client Client to make calls to ElasticSearch
     * @param clusterService ES ClusterService
     */
    @Inject
    public IndexUtils(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
    }

    /**
     * Gets the cluster index health for a particular index or the index an alias points to
     *
     * If an alias is passed in, it will only return the health status of an index it points to if it only points to a
     * single index. If it points to multiple indices, it will throw an exception.
     *
     * @param indexOrAliasName String of the index or alias name to get health of.
     * @return String represents the status of the index: "red", "yellow" or "green"
     * @throws IllegalArgumentException Thrown when an alias is passed in that points to more than one index
     */
    public String getIndexHealthStatus(String indexOrAliasName) throws IllegalArgumentException {
        if (!clusterService.state().getRoutingTable().hasIndex(indexOrAliasName)) {
            // Check if the index is actually an alias
            if (clusterService.state().metadata().hasAlias(indexOrAliasName)) {
                // List of all indices the alias refers to
                List<IndexMetadata> indexMetaDataList = clusterService
                    .state()
                    .metadata()
                    .getIndicesLookup()
                    .get(indexOrAliasName)
                    .getIndices();
                if (indexMetaDataList.size() == 0) {
                    return ALIAS_EXISTS_NO_INDICES_STATUS;
                } else if (indexMetaDataList.size() > 1) {
                    throw new IllegalArgumentException("Cannot get health for alias that points to multiple indices");
                } else {
                    indexOrAliasName = indexMetaDataList.get(0).getIndex().getName();
                }
            } else {
                return NONEXISTENT_INDEX_STATUS;
            }
        }

        ClusterIndexHealth indexHealth = new ClusterIndexHealth(
            clusterService.state().metadata().index(indexOrAliasName),
            clusterService.state().getRoutingTable().index(indexOrAliasName)
        );

        return indexHealth.getStatus().name().toLowerCase(Locale.ROOT);
    }

    public void getNumberOfDocumentsInIndex(String indexName, ActionListener<Long> listener) {
        if (clusterService.state().getRoutingTable().hasIndex(indexName)) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.indices(indexName);
            client.admin().indices().stats(indicesStatsRequest, ActionListener.wrap(r -> {
                long count = r.getIndex(indexName).getPrimaries().docs.getCount();
                listener.onResponse(count);
            }, e -> { listener.onFailure(e); }));
        } else {
            listener.onResponse(0L);
        }
    }

}

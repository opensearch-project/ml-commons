/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.index.shard.ShardPath;
import org.opensearch.ml.common.spi.tools.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.admin.indices.stats.CommonStats;
import org.opensearch.action.admin.indices.stats.CommonStatsFlags;
import org.opensearch.action.admin.indices.stats.IndexStats;
import org.opensearch.action.admin.indices.stats.IndicesStatsResponse;
import org.opensearch.action.admin.indices.stats.ShardStats;
import org.opensearch.action.admin.indices.stats.IndexStats.IndexStatsBuilder;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.ClusterAdminClient;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.IndexMetadata.State;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.cluster.routing.IndexShardRoutingTable;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.cluster.routing.ShardRoutingState;
import org.opensearch.cluster.routing.TestShardRouting;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.UUIDs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CatIndexToolTests {

    @Mock
    private Client client;

    @Mock
    private AdminClient adminClient;

    @Mock
    private IndicesAdminClient indicesAdminClient;

    @Mock
    private ClusterAdminClient clusterAdminClient;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ClusterState clusterState;

    @Mock
    private Metadata metadata;

    @Mock
    private IndicesStatsResponse indicesStatsResponse;

    @Mock
    private ClusterHealthResponse clusterHealthResponse;

    @Mock
    private IndexMetadata indexMetadata;

    @Mock
    private IndexRoutingTable indexRoutingTable;

    private Map<String, String> indicesParams;
    private Map<String, String> otherParams;
    private Map<String, String> emptyParams;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);
        when(client.admin()).thenReturn(adminClient);

        when(indexMetadata.getState()).thenReturn(State.OPEN);
        when(metadata.index(any(String.class))).thenReturn(indexMetadata);
        when(clusterState.metadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);

        CatIndexTool.Factory.getInstance().init(client, clusterService);

        indicesParams = Map.of("indices", "foo");
        otherParams = Map.of("other", "bar");
        emptyParams = Collections.emptyMap();
    }

    @Test
    public void testRunAsyncNoIndices() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<IndicesStatsResponse>> actionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(indicesAdminClient).stats(any(), actionListenerCaptor.capture());
        when(indicesStatsResponse.getIndices()).thenReturn(Collections.emptyMap());

        Tool tool = CatIndexTool.Factory.getInstance().create(Map.of("model_id", "test"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });

        tool.run(otherParams, listener);
        actionListenerCaptor.getValue().onResponse(indicesStatsResponse);
        future.join();
        assertEquals("There were no results searching the indices parameter [null].", future.get());
    }

    @Test
    public void testRunAsyncIndexStats() throws Exception {
        String indexName = "foo";
        Index index = new Index(indexName, UUIDs.base64UUID());

        // Setup indices query
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<IndicesStatsResponse>> indicesStatsListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(indicesAdminClient).stats(any(), indicesStatsListenerCaptor.capture());

        int shardId = 0;
        ShardId shId = new ShardId(index, shardId);
        Path path = Files.createTempDirectory("temp").resolve("indices").resolve(index.getUUID()).resolve(String.valueOf(shardId));
        ShardPath shardPath = new ShardPath(false, path, path, shId);
        ShardRouting routing = TestShardRouting.newShardRouting(shId, "node", true, ShardRoutingState.STARTED);
        CommonStats commonStats = new CommonStats(CommonStatsFlags.ALL);
        IndexStats fooStats = new IndexStatsBuilder(index.getName(), index.getUUID()).add(
            new ShardStats(routing, shardPath, commonStats, null, null, null)
        ).build();
        when(indicesStatsResponse.getIndices()).thenReturn(Map.of(indexName, fooStats));

        // Setup cluster health query
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<ClusterHealthResponse>> clusterHealthListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(clusterAdminClient).health(any(), clusterHealthListenerCaptor.capture());

        when(indexMetadata.getIndex()).thenReturn(index);
        when(indexMetadata.getNumberOfShards()).thenReturn(1);
        when(indexMetadata.getNumberOfReplicas()).thenReturn(0);
        @SuppressWarnings("unchecked")
        Iterator<IndexShardRoutingTable> iterator = (Iterator<IndexShardRoutingTable>) mock(Iterator.class);
        when(iterator.hasNext()).thenReturn(false);
        when(indexRoutingTable.iterator()).thenReturn(iterator);
        ClusterIndexHealth fooHealth = new ClusterIndexHealth(indexMetadata, indexRoutingTable);
        when(clusterHealthResponse.getIndices()).thenReturn(Map.of(indexName, fooHealth));

        // Now make the call
        Tool tool = CatIndexTool.Factory.getInstance().create(Map.of("model_id", "test"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });

        tool.run(otherParams, listener);
        indicesStatsListenerCaptor.getValue().onResponse(indicesStatsResponse);
        clusterHealthListenerCaptor.getValue().onResponse(clusterHealthResponse);
        future.orTimeout(10, TimeUnit.SECONDS).join();
        String response = future.get();
        assertEquals(
            "health\tstatus\tindex\tuuid\tpri\trep\tdocs.count\tdocs.deleted\tstore.size\tpri.store.size\n"
                + "red\tOPEN\tfoo\tnull\t1\t0\t0\t0\t0b\t0b\n",
            response
        );
    }

    @Test
    public void testRun() {
        Tool tool = CatIndexTool.Factory.getInstance().create(Map.of("model_id", "test"));
        // TODO This is not implemented on the interface, need to change this test if/when it is
        assertNull(tool.run(emptyParams));
    }

    @Test
    public void testTool() {
        Tool tool = CatIndexTool.Factory.getInstance().create(Map.of("model_id", "test"));
        assertEquals(CatIndexTool.NAME, tool.getName());
        assertTrue(tool.validate(indicesParams));
        assertTrue(tool.validate(otherParams));
        assertFalse(tool.validate(emptyParams));
    }
}

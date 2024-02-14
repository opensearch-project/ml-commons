/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.opensearch.action.admin.indices.stats.CommonStats;
import org.opensearch.action.admin.indices.stats.CommonStatsFlags;
import org.opensearch.action.admin.indices.stats.IndexStats;
import org.opensearch.action.admin.indices.stats.IndexStats.IndexStatsBuilder;
import org.opensearch.action.admin.indices.stats.IndicesStatsResponse;
import org.opensearch.action.admin.indices.stats.ShardStats;
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
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.index.shard.ShardPath;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.tools.CatIndexTool.Factory;

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
    private GetSettingsResponse getSettingsResponse;
    @Mock
    private IndicesStatsResponse indicesStatsResponse;
    @Mock
    private ClusterStateResponse clusterStateResponse;
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
        when(indexMetadata.getCreationVersion()).thenReturn(Version.CURRENT);

        when(metadata.index(any(String.class))).thenReturn(indexMetadata);
        when(clusterState.metadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);

        CatIndexTool.Factory.getInstance().init(client, clusterService);

        indicesParams = Map.of("index", "[\"foo\"]");
        otherParams = Map.of("other", "[\"bar\"]");
        emptyParams = Collections.emptyMap();
    }

    @Test
    public void testRunAsyncNoIndices() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<GetSettingsResponse>> settingsActionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(indicesAdminClient).getSettings(any(), settingsActionListenerCaptor.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<IndicesStatsResponse>> statsActionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(indicesAdminClient).stats(any(), statsActionListenerCaptor.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<ClusterStateResponse>> clusterStateActionListenerCaptor = ArgumentCaptor
            .forClass(ActionListener.class);
        doNothing().when(clusterAdminClient).state(any(), clusterStateActionListenerCaptor.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<ClusterHealthResponse>> clusterHealthActionListenerCaptor = ArgumentCaptor
            .forClass(ActionListener.class);
        doNothing().when(clusterAdminClient).health(any(), clusterHealthActionListenerCaptor.capture());

        when(getSettingsResponse.getIndexToSettings()).thenReturn(Collections.emptyMap());
        when(indicesStatsResponse.getIndices()).thenReturn(Collections.emptyMap());
        when(clusterStateResponse.getState()).thenReturn(clusterState);
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(metadata.spliterator()).thenReturn(Arrays.spliterator(new IndexMetadata[0]));

        when(clusterHealthResponse.getIndices()).thenReturn(Collections.emptyMap());

        Tool tool = CatIndexTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });

        tool.run(otherParams, listener);
        settingsActionListenerCaptor.getValue().onResponse(getSettingsResponse);
        statsActionListenerCaptor.getValue().onResponse(indicesStatsResponse);
        clusterStateActionListenerCaptor.getValue().onResponse(clusterStateResponse);
        clusterHealthActionListenerCaptor.getValue().onResponse(clusterHealthResponse);

        future.join();
        assertEquals("There were no results searching the indices parameter [null].", future.get());
    }

    @Test
    public void testRunAsyncIndexStats() throws Exception {
        String indexName = "foo";
        Index index = new Index(indexName, UUIDs.base64UUID());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<GetSettingsResponse>> settingsActionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(indicesAdminClient).getSettings(any(), settingsActionListenerCaptor.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<IndicesStatsResponse>> statsActionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doNothing().when(indicesAdminClient).stats(any(), statsActionListenerCaptor.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<ClusterStateResponse>> clusterStateActionListenerCaptor = ArgumentCaptor
            .forClass(ActionListener.class);
        doNothing().when(clusterAdminClient).state(any(), clusterStateActionListenerCaptor.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<ClusterHealthResponse>> clusterHealthActionListenerCaptor = ArgumentCaptor
            .forClass(ActionListener.class);
        doNothing().when(clusterAdminClient).health(any(), clusterHealthActionListenerCaptor.capture());

        when(getSettingsResponse.getIndexToSettings()).thenReturn(Map.of("foo", Settings.EMPTY));

        int shardId = 0;
        ShardId shId = new ShardId(index, shardId);
        Path path = Files.createTempDirectory("temp").resolve("indices").resolve(index.getUUID()).resolve(String.valueOf(shardId));
        ShardPath shardPath = new ShardPath(false, path, path, shId);
        ShardRouting routing = TestShardRouting.newShardRouting(shId, "node", true, ShardRoutingState.STARTED);
        CommonStats commonStats = new CommonStats(CommonStatsFlags.ALL);
        IndexStats fooStats = new IndexStatsBuilder(index.getName(), index.getUUID())
            .add(new ShardStats(routing, shardPath, commonStats, null, null, null))
            .build();
        when(indicesStatsResponse.getIndices()).thenReturn(Map.of(indexName, fooStats));

        when(indexMetadata.getIndex()).thenReturn(index);
        when(indexMetadata.getNumberOfShards()).thenReturn(5);
        when(indexMetadata.getNumberOfReplicas()).thenReturn(1);
        when(clusterStateResponse.getState()).thenReturn(clusterState);
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(metadata.spliterator()).thenReturn(Arrays.spliterator(new IndexMetadata[] { indexMetadata }));
        @SuppressWarnings("unchecked")
        Iterator<IndexShardRoutingTable> iterator = (Iterator<IndexShardRoutingTable>) mock(Iterator.class);
        when(iterator.hasNext()).thenReturn(false);
        when(indexRoutingTable.iterator()).thenReturn(iterator);
        ClusterIndexHealth fooHealth = new ClusterIndexHealth(indexMetadata, indexRoutingTable);
        when(clusterHealthResponse.getIndices()).thenReturn(Map.of(indexName, fooHealth));

        // Now make the call
        Tool tool = CatIndexTool.Factory.getInstance().create(Collections.emptyMap());
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });

        tool.run(otherParams, listener);
        settingsActionListenerCaptor.getValue().onResponse(getSettingsResponse);
        statsActionListenerCaptor.getValue().onResponse(indicesStatsResponse);
        clusterStateActionListenerCaptor.getValue().onResponse(clusterStateResponse);
        clusterHealthActionListenerCaptor.getValue().onResponse(clusterHealthResponse);

        future.orTimeout(10, TimeUnit.SECONDS).join();
        String response = future.get();
        String[] responseRows = response.trim().split("\\n");

        assertEquals(2, responseRows.length);
        String header = responseRows[0];
        String fooRow = responseRows[1];
        assertEquals(header.split("\\t").length, fooRow.split("\\t").length);
        assertEquals(
            "row,health,status,index,uuid,pri(number of primary shards),rep(number of replica shards),docs.count(number of available documents),docs.deleted(number of deleted documents),store.size(store size of primary and replica shards),pri.store.size(store size of primary shards)",
            header
        );
        assertEquals("1,red,open,foo,null,5,1,0,0,0b,0b", fooRow);
    }

    @Test
    public void testTool() {
        Factory instance = CatIndexTool.Factory.getInstance();
        assertEquals(instance, CatIndexTool.Factory.getInstance());
        assertTrue(instance.getDefaultDescription().contains("tool"));

        Tool tool = instance.create(Collections.emptyMap());
        assertEquals(CatIndexTool.TYPE, tool.getType());
        assertTrue(tool.validate(indicesParams));
        assertTrue(tool.validate(otherParams));
        assertFalse(tool.validate(emptyParams));
    }
}

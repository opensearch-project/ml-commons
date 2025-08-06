package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.tools.SearchIndexTool.INPUT_SCHEMA_FIELD;
import static org.opensearch.ml.engine.tools.SearchIndexTool.STRICT_FIELD;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
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
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.cluster.routing.IndexShardRoutingTable;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.UUIDs;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.unit.ByteSizeValue;
import org.opensearch.core.index.Index;
import org.opensearch.index.shard.DocsStats;
import org.opensearch.index.store.StoreStats;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.ClusterAdminClient;
import org.opensearch.transport.client.IndicesAdminClient;

import com.google.common.collect.ImmutableMap;

public class ListIndexToolTests {
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
    private IndexMetadata indexMetadata;
    @Mock
    private IndexRoutingTable indexRoutingTable;
    @Mock
    private Index index;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);
        when(client.admin()).thenReturn(adminClient);

        when(indexMetadata.getState()).thenReturn(IndexMetadata.State.OPEN);
        when(indexMetadata.getCreationVersion()).thenReturn(Version.CURRENT);

        when(metadata.index(any(String.class))).thenReturn(indexMetadata);
        when(indexMetadata.getIndex()).thenReturn(index);
        when(indexMetadata.getIndexUUID()).thenReturn(UUIDs.base64UUID());
        when(index.getName()).thenReturn("index-1");
        when(clusterState.metadata()).thenReturn(metadata);
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);

        ListIndexTool.Factory.getInstance().init(client, clusterService);
    }

    @Test
    public void test_getType() {
        Tool tool = ListIndexTool.Factory.getInstance().create(Collections.emptyMap());
        assert (tool.getType().equals("ListIndexTool"));
    }

    @Test
    public void test_getDefaultAttributes() {
        Map<String, Object> attributes = ListIndexTool.Factory.getInstance().create(Collections.emptyMap()).getAttributes();
        assertEquals(
            "{\"type\":\"object\",\"properties\":"
                + "{\"indices\":{\"type\":\"array\",\"items\": {\"type\": \"string\"},"
                + "\"description\":\"OpenSearch index name list, separated by comma. "
                + "for example: [\\\"index1\\\", \\\"index2\\\"], use empty array [] to list all indices in the cluster\"}},"
                + "\"additionalProperties\":false}",
            attributes.get(INPUT_SCHEMA_FIELD)
        );
        assertEquals(false, attributes.get(STRICT_FIELD));
    }

    @Test
    public void test_run_successful_1() {
        mockUp();
        Tool tool = ListIndexTool.Factory.getInstance().create(Collections.emptyMap());
        verifyResult(tool, createParameters("[\"index-1\"]", "true", "10", "true"));
    }

    @Test
    public void test_run_successful_2() {
        mockUp();
        Tool tool = ListIndexTool.Factory.getInstance().create(Collections.emptyMap());
        verifyResult(tool, createParameters(null, null, null, null));
    }

    private Map<String, String> createParameters(String indices, String local, String pageSize, String includeUnloadedSegments) {
        Map<String, String> parameters = new HashMap<>();
        if (indices != null) {
            parameters.put("indices", indices);
        }
        if (local != null) {
            parameters.put("local", local);
        }
        if (pageSize != null) {
            parameters.put("page_size", pageSize);
        }
        if (includeUnloadedSegments != null) {
            parameters.put("include_unloaded_segments", includeUnloadedSegments);
        }
        return parameters;
    }

    private void verifyResult(Tool tool, Map<String, String> parameters) {
        ActionListener<String> listener = mock(ActionListener.class);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        tool.run(parameters, listener);
        verify(listener).onResponse(captor.capture());
        System.out.println(captor.getValue());
        assert captor.getValue().contains("1,red,open,index-1");
        assert captor.getValue().contains("5,1,100,10,100kb,100kb");
    }

    private void mockUp() {
        doAnswer(invocation -> {
            ActionListener<GetSettingsResponse> actionListener = invocation.getArgument(1);
            GetSettingsResponse response = mock(GetSettingsResponse.class);
            Map<String, Settings> indexToSettings = new HashMap<>();
            indexToSettings.put("index-1", Settings.EMPTY);
            indexToSettings.put("index-2", Settings.EMPTY);
            when(response.getIndexToSettings()).thenReturn(indexToSettings);
            actionListener.onResponse(response);
            return null;
        }).when(indicesAdminClient).getSettings(any(GetSettingsRequest.class), isA(ActionListener.class));

        // clusterStateResponse.getState().getMetadata().spliterator()
        doAnswer(invocation -> {
            ActionListener<ClusterStateResponse> actionListener = invocation.getArgument(1);
            ClusterStateResponse response = mock(ClusterStateResponse.class);
            when(response.getState()).thenReturn(clusterState);
            actionListener.onResponse(response);
            return null;
        }).when(clusterAdminClient).state(any(ClusterStateRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndicesStatsResponse> actionListener = invocation.getArgument(1);
            IndicesStatsResponse response = mock(IndicesStatsResponse.class);
            Map<String, IndexStats> indicesStats = new HashMap<>();
            IndexStats indexStats = mock(IndexStats.class);
            // mock primary stats
            CommonStats primaryStats = mock(CommonStats.class);
            DocsStats docsStats = mock(DocsStats.class);
            when(docsStats.getCount()).thenReturn(100L);
            when(docsStats.getDeleted()).thenReturn(10L);
            when(primaryStats.getDocs()).thenReturn(docsStats);
            StoreStats primaryStoreStats = mock(StoreStats.class);
            when(primaryStoreStats.size()).thenReturn(ByteSizeValue.parseBytesSizeValue("100k", "mock_setting_name"));
            when(primaryStats.getStore()).thenReturn(primaryStoreStats);
            // end mock primary stats

            // mock total stats
            CommonStats totalStats = mock(CommonStats.class);
            DocsStats totalDocsStats = mock(DocsStats.class);
            when(totalDocsStats.getCount()).thenReturn(100L);
            when(totalDocsStats.getDeleted()).thenReturn(10L);
            StoreStats totalStoreStats = mock(StoreStats.class);
            when(totalStoreStats.size()).thenReturn(ByteSizeValue.parseBytesSizeValue("100k", "mock_setting_name"));
            when(totalStats.getStore()).thenReturn(totalStoreStats);
            // end mock common stats

            when(indexStats.getPrimaries()).thenReturn(primaryStats);
            when(indexStats.getTotal()).thenReturn(totalStats);
            indicesStats.put("index-1", indexStats);
            when(response.getIndices()).thenReturn(indicesStats);
            actionListener.onResponse(response);
            return null;
        }).when(indicesAdminClient).stats(any(IndicesStatsRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<ClusterHealthResponse> actionListener = invocation.getArgument(1);
            ClusterHealthResponse response = mock(ClusterHealthResponse.class);
            Map<String, ClusterIndexHealth> clusterIndexHealthMap = new HashMap<>();
            when(indexMetadata.getNumberOfShards()).thenReturn(5);
            when(indexMetadata.getNumberOfReplicas()).thenReturn(1);
            when(metadata.spliterator()).thenReturn(Arrays.spliterator(new IndexMetadata[] { indexMetadata }));
            Iterator<IndexShardRoutingTable> iterator = (Iterator<IndexShardRoutingTable>) mock(Iterator.class);
            when(iterator.hasNext()).thenReturn(false);
            when(indexRoutingTable.iterator()).thenReturn(iterator);
            ClusterIndexHealth health = new ClusterIndexHealth(indexMetadata, indexRoutingTable);
            clusterIndexHealthMap.put("index-1", health);
            when(response.getIndices()).thenReturn(clusterIndexHealthMap);
            actionListener.onResponse(response);
            return null;
        }).when(clusterAdminClient).health(any(ClusterHealthRequest.class), isA(ActionListener.class));
    }

    @Test
    public void test_run_withEmptyTableResult() {
        Map<String, String> parameters = createParameters("[\"index-1\"]", "true", "10", "true");
        Tool tool = ListIndexTool.Factory.getInstance().create(Collections.emptyMap());
        doAnswer(invocation -> {
            ActionListener<GetSettingsResponse> actionListener = invocation.getArgument(1);
            GetSettingsResponse response = mock(GetSettingsResponse.class);
            Map<String, Settings> indexToSettings = new HashMap<>();
            indexToSettings.put("index-1", Settings.EMPTY);
            indexToSettings.put("index-2", Settings.EMPTY);
            when(response.getIndexToSettings()).thenReturn(indexToSettings);
            actionListener.onResponse(response);
            return null;
        }).when(indicesAdminClient).getSettings(any(GetSettingsRequest.class), isA(ActionListener.class));

        // clusterStateResponse.getState().getMetadata().spliterator()
        doAnswer(invocation -> {
            ActionListener<ClusterStateResponse> actionListener = invocation.getArgument(1);
            ClusterStateResponse response = mock(ClusterStateResponse.class);
            when(response.getState()).thenReturn(clusterState);
            actionListener.onResponse(response);
            return null;
        }).when(clusterAdminClient).state(any(ClusterStateRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndicesStatsResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(null);
            return null;
        }).when(indicesAdminClient).stats(any(IndicesStatsRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<ClusterHealthResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(null);
            return null;
        }).when(clusterAdminClient).health(any(ClusterHealthRequest.class), isA(ActionListener.class));

        ActionListener<String> listener = mock(ActionListener.class);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        tool.run(parameters, listener);
        verify(listener).onResponse(captor.capture());
        System.out.println(captor.getValue());
        assert captor.getValue().contains("There were no results searching the indices parameter");
    }

    @Test
    public void test_run_failed() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("indices", "[\"index-1\"]");
        parameters.put("page_size", "10");

        doAnswer(invocation -> {
            ActionListener<GetSettingsResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("failed to get settings"));
            return null;
        }).when(indicesAdminClient).getSettings(any(GetSettingsRequest.class), isA(ActionListener.class));

        Tool tool = ListIndexTool.Factory.getInstance().create(Collections.emptyMap());
        ActionListener<String> listener = mock(ActionListener.class);
        ArgumentCaptor<RuntimeException> captor = ArgumentCaptor.forClass(RuntimeException.class);
        tool.run(parameters, listener);
        verify(listener).onFailure(captor.capture());
        System.out.println(captor.getValue().getMessage());
        assert (captor.getValue().getMessage().contains("failed to get settings"));
    }

    @Test
    public void test_validate() {
        Tool tool = ListIndexTool.Factory.getInstance().create(Collections.emptyMap());
        assert tool.validate(ImmutableMap.of("runtimeParameter", "value1"));
        assert !tool.validate(null);
        assert !tool.validate(Collections.emptyMap());
    }

    @Test
    public void test_getDefaultDescription() {
        Tool.Factory<ListIndexTool> factory = ListIndexTool.Factory.getInstance();
        System.out.println(factory.getDefaultDescription());
        assert (factory.getDefaultDescription().equals(ListIndexTool.DEFAULT_DESCRIPTION));
    }

    @Test
    public void test_getDefaultType() {
        Tool.Factory<ListIndexTool> factory = ListIndexTool.Factory.getInstance();
        System.out.println(factory.getDefaultType());
        assert (factory.getDefaultType().equals("ListIndexTool"));
    }

    @Test
    public void test_getDefaultVersion() {
        Tool.Factory<ListIndexTool> factory = ListIndexTool.Factory.getInstance();
        assert factory.getDefaultVersion() == null;
    }

    @Test
    public void test_run_withGeneralException() {
        ListIndexTool tool = new ListIndexTool(null, clusterService);
        ActionListener<String> listener = mock(ActionListener.class);
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);

        Map<String, String> parameters = createParameters("[\"index-1\"]", "true", "10", "true");
        tool.run(parameters, listener);

        verify(listener).onFailure(captor.capture());
        assert captor.getValue() instanceof Exception;
    }
}

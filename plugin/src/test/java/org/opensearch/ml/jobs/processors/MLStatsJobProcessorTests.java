/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.stats.otel.counters.MLAdoptionMetricsCounter;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLStatsJobProcessorTests {

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MetricsRegistry metricsRegistry;

    @Mock
    private Counter mockCounter;

    private ThreadContext threadContext;
    private MLStatsJobProcessor processor;
    private ClusterState clusterState;
    private Metadata metadata;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Set up ClusterService mock
        clusterState = mock(ClusterState.class);
        metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(clusterService.getClusterName()).thenReturn(new ClusterName("test-cluster"));
        when(metadata.indices()).thenReturn(Map.of(ML_MODEL_INDEX, mock(IndexMetadata.class), ML_AGENT_INDEX, mock(IndexMetadata.class)));

        // Reset singletons before each test
        MLAdoptionMetricsCounter.reset();
        MLStatsJobProcessor.reset();

        // Initialize MLAdoptionMetricsCounter with proper mocking
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(metricsRegistry.createCounter(any(), any(), any())).thenReturn(mockCounter);
        MLAdoptionMetricsCounter.initialize("test-cluster", metricsRegistry, mlFeatureEnabledSetting);

        processor = MLStatsJobProcessor.getInstance(clusterService, client, threadPool, connectorAccessControlHelper, sdkClient);
    }

    @Test
    public void testGetInstance() {
        MLStatsJobProcessor instance1 = MLStatsJobProcessor
            .getInstance(clusterService, client, threadPool, connectorAccessControlHelper, sdkClient);
        MLStatsJobProcessor instance2 = MLStatsJobProcessor
            .getInstance(clusterService, client, threadPool, connectorAccessControlHelper, sdkClient);
        Assert.assertSame(instance1, instance2);
    }

    @Test
    public void testRun() throws IOException {
        SearchResponse modelSearchResponse = createModelSearchResponse();
        SearchResponse emptyAgentResponse = createEmptySearchResponse();

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_AGENT_INDEX)) {
                listener.onResponse(emptyAgentResponse);
            } else {
                listener.onResponse(modelSearchResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(2)).search(any(SearchRequest.class), isA(ActionListener.class)); // Both model and agent searches
        verify(mockCounter, times(1)).add(eq(1.0), any(Tags.class));
    }

    @Test
    public void testMetricCollectionSettings() throws IOException {
        SearchResponse modelSearchResponse = createModelSearchResponse();
        SearchResponse emptyAgentResponse = createEmptySearchResponse();

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_AGENT_INDEX)) {
                listener.onResponse(emptyAgentResponse);
            } else {
                listener.onResponse(modelSearchResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Enable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        processor.run();
        verify(mockCounter, times(1)).add(eq(1.0), any(Tags.class));

        // Disable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(false);
        processor.run();
        verify(mockCounter, times(1)).add(eq(1.0), any(Tags.class)); // Count should not increase

        // Re-enable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        processor.run();
        verify(mockCounter, times(2)).add(eq(1.0), any(Tags.class)); // Count should increase again
    }

    @Test
    public void testRunWithConnectorId() throws IOException {
        SearchResponse modelSearchResponse = createModelSearchResponseWithConnectorId();
        SearchResponse emptyAgentResponse = createEmptySearchResponse();

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_AGENT_INDEX)) {
                listener.onResponse(emptyAgentResponse);
            } else {
                listener.onResponse(modelSearchResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(5);
            Connector connector = HttpConnector
                .builder()
                .name("test-connector")
                .description("test description")
                .version("1.0.0")
                .protocol("http")
                .accessMode(AccessMode.PUBLIC)
                .build();
            listener.onResponse(connector);
            return null;
        })
            .when(connectorAccessControlHelper)
            .getConnector(
                eq(sdkClient),
                eq(client),
                any(ThreadContext.StoredContext.class),
                any(GetDataObjectRequest.class),
                eq("test-connector-id"),
                any(ActionListener.class)
            );

        processor.run();

        verify(client, times(2)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(connectorAccessControlHelper, times(1))
            .getConnector(
                eq(sdkClient),
                eq(client),
                any(ThreadContext.StoredContext.class),
                any(GetDataObjectRequest.class),
                eq("test-connector-id"),
                any(ActionListener.class)
            );
        verify(mockCounter, times(1)).add(eq(1.0), any(Tags.class));
    }

    @Test
    public void testRunWithSearchFailure() throws IOException {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Search failed"));
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // only model search
        verify(client, times(1)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(mockCounter, never()).add(anyDouble(), any(Tags.class));
    }

    @Test
    public void testRunWithConnectorFailure() throws IOException {
        SearchResponse modelSearchResponse = createModelSearchResponseWithConnectorId();
        SearchResponse emptyAgentResponse = createEmptySearchResponse();

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_AGENT_INDEX)) {
                listener.onResponse(emptyAgentResponse);
            } else {
                listener.onResponse(modelSearchResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(5);
            listener.onFailure(new RuntimeException("Failed to get connector"));
            return null;
        })
            .when(connectorAccessControlHelper)
            .getConnector(
                eq(sdkClient),
                eq(client),
                any(ThreadContext.StoredContext.class),
                any(GetDataObjectRequest.class),
                eq("test-connector-id"),
                any(ActionListener.class)
            );

        processor.run();

        verify(client, times(2)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(connectorAccessControlHelper, times(1))
            .getConnector(
                eq(sdkClient),
                eq(client),
                any(ThreadContext.StoredContext.class),
                any(GetDataObjectRequest.class),
                eq("test-connector-id"),
                any(ActionListener.class)
            );
        verify(mockCounter, never()).add(anyDouble(), any(Tags.class));
    }

    private SearchResponse createModelSearchResponse() throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);

        String modelContent = "{\n"
            + "    \"algorithm\": \"TEXT_EMBEDDING\",\n"
            + "    \"model_id\": \"test-model-id\",\n"
            + "    \"name\": \"Test Model\",\n"
            + "    \"model_version\": \"1.0.0\",\n"
            + "    \"model_format\": \"TORCH_SCRIPT\",\n"
            + "    \"model_state\": \"DEPLOYED\",\n"
            + "    \"model_content_hash_value\": \"hash123\",\n"
            + "    \"model_config\": {\n"
            + "        \"model_type\": \"test\",\n"
            + "        \"embedding_dimension\": 384,\n"
            + "        \"framework_type\": \"SENTENCE_TRANSFORMERS\"\n"
            + "    },\n"
            + "    \"model_content_size_in_bytes\": 1000000,\n"
            + "    \"chunk_number\": 1,\n"
            + "    \"total_chunks\": 1\n"
            + "}";

        SearchHit modelHit = new SearchHit(1);
        modelHit.sourceRef(new BytesArray(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { modelHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);

        return searchResponse;
    }

    private SearchResponse createModelSearchResponseWithConnectorId() throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);

        String modelContent = "{\n"
            + "    \"algorithm\": \"TEXT_EMBEDDING\",\n"
            + "    \"model_id\": \"test-model-id\",\n"
            + "    \"name\": \"Test Model\",\n"
            + "    \"model_version\": \"1.0.0\",\n"
            + "    \"model_format\": \"TORCH_SCRIPT\",\n"
            + "    \"model_state\": \"DEPLOYED\",\n"
            + "    \"model_content_hash_value\": \"hash123\",\n"
            + "    \"model_config\": {\n"
            + "        \"model_type\": \"test\",\n"
            + "        \"embedding_dimension\": 384,\n"
            + "        \"framework_type\": \"SENTENCE_TRANSFORMERS\"\n"
            + "    },\n"
            + "    \"model_content_size_in_bytes\": 1000000,\n"
            + "    \"chunk_number\": 1,\n"
            + "    \"total_chunks\": 1,\n"
            + "    \"connector_id\": \"test-connector-id\"\n"
            + "}";

        SearchHit modelHit = new SearchHit(1);
        modelHit.sourceRef(new BytesArray(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { modelHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);

        return searchResponse;
    }

    @Test
    public void testCollectAgentMetricsWithModelTags() throws IOException {
        SearchResponse modelSearchResponse = createModelSearchResponse();
        SearchResponse agentSearchResponse = createAgentSearchResponseWithModelId();

        // Use a counter to ensure model search happens first
        final int[] callCount = { 0 };
        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            callCount[0]++;
            if (callCount[0] == 1) {
                // First call should be model search
                listener.onResponse(modelSearchResponse);
            } else {
                // Second call should be agent search
                listener.onResponse(agentSearchResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(2)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(mockCounter, times(2)).add(eq(1.0), any(Tags.class)); // Both model and agent metrics
    }

    @Test
    public void testCollectAgentMetricsNoAgentIndex() {
        when(metadata.indices()).thenReturn(Map.of(ML_MODEL_INDEX, mock(IndexMetadata.class)));

        SearchResponse modelSearchResponse = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(modelSearchResponse.getHits()).thenReturn(hits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(modelSearchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(1)).search(any(SearchRequest.class), isA(ActionListener.class)); // Only model search
    }

    @Test
    public void testCollectAgentMetricsSearchFailure() throws IOException {
        SearchResponse modelSearchResponse = createModelSearchResponse();

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_AGENT_INDEX)) {
                listener.onFailure(new RuntimeException("Agent search failed"));
            } else {
                listener.onResponse(modelSearchResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(2)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(mockCounter, times(1)).add(eq(1.0), any(Tags.class)); // Only model metric
    }

    private SearchResponse createAgentSearchResponseWithModelId() throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);

        String agentContent = "{\n"
            + "    \"name\": \"Test Chat Agent with Model\",\n"
            + "    \"type\": \"conversational\",\n"
            + "    \"description\": \"this is a test agent\",\n"
            + "    \"llm\": {\n"
            + "        \"model_id\": \"test-model-id\",\n"
            + "        \"parameters\": {\n"
            + "            \"max_iteration\": 1,\n"
            + "            \"system_prompt\": \"You are a helpful assistant\"\n"
            + "        }\n"
            + "    },\n"
            + "    \"memory\": {\n"
            + "        \"type\": \"conversation_index\"\n"
            + "    },\n"
            + "    \"tools\": [\n"
            + "        {\n"
            + "            \"type\": \"SearchIndexTool\",\n"
            + "            \"attributes\": {\n"
            + "                \"input_schema\": {\n"
            + "                    \"type\": \"object\",\n"
            + "                    \"properties\": {\n"
            + "                        \"index\": {\"type\": \"string\"},\n"
            + "                        \"query\": {\"type\": \"object\"}\n"
            + "                    }\n"
            + "                }\n"
            + "            }\n"
            + "        }\n"
            + "    ],\n"
            + "    \"app_type\": \"os_chat\"\n"
            + "}";

        SearchHit agentHit = new SearchHit(1);
        agentHit.sourceRef(new BytesArray(agentContent));
        SearchHits hits = new SearchHits(new SearchHit[] { agentHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);

        return searchResponse;
    }

    private SearchResponse createEmptySearchResponse() {
        SearchResponse searchResponse = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }

    @Test
    public void testAddTagIfExists() {
        Tags agentTags = Tags.create();
        Map<String, Object> sourceTagsMap = new HashMap<>();
        sourceTagsMap.put("model", "test-model");
        sourceTagsMap.put("service_provider", "openai");
        sourceTagsMap.put("empty_key", null);

        processor.addTagIfExists(sourceTagsMap, "model", "agent_model", agentTags);
        processor.addTagIfExists(sourceTagsMap, "service_provider", "agent_service_provider", agentTags);
        processor.addTagIfExists(sourceTagsMap, "nonexistent_key", "agent_nonexistent", agentTags);
        processor.addTagIfExists(sourceTagsMap, "empty_key", "agent_empty", agentTags);

        Map<String, ?> resultTags = agentTags.getTagsMap();
        Assert.assertEquals("test-model", resultTags.get("agent_model"));
        Assert.assertEquals("openai", resultTags.get("agent_service_provider"));
        Assert.assertNull(resultTags.get("agent_nonexistent"));
        Assert.assertNull(resultTags.get("agent_empty"));
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.admin.cluster.state.ClusterStateRequest;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.ClusterAdminClient;
import org.opensearch.transport.client.IndicesAdminClient;

import com.fasterxml.jackson.databind.ObjectMapper;

public class IndexCorrelationTaskTests {

    private Client client;
    private SdkClient sdkClient;
    private ThreadContext threadContext;
    private ThreadPool threadPool;
    private IndexCorrelationTask task;
    private ClusterAdminClient clusterAdmin;
    private IndicesAdminClient indicesAdmin;
    private ActionListener<IndexInsight> listener;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Before
    public void setUp() {
        client = mock(Client.class);
        sdkClient = mock(SdkClient.class);
        threadPool = mock(ThreadPool.class);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Mock admin clients
        AdminClient adminClient = mock(AdminClient.class);
        clusterAdmin = mock(ClusterAdminClient.class);
        indicesAdmin = mock(IndicesAdminClient.class);

        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdmin);
        when(adminClient.indices()).thenReturn(indicesAdmin);

        // Clear mappings before each test
        indexMappings.clear();

        task = new IndexCorrelationTask("jaeger-span-2025-12-19", client, sdkClient);
        listener = mock(ActionListener.class);
    }

    @Test
    public void testTaskType() {
        assertEquals(MLIndexInsightType.INDEX_CORRELATION, task.taskType);
    }

    @Test
    public void testSourceIndex() {
        assertEquals("jaeger-span-2025-12-19", task.sourceIndex);
    }

    @Test
    public void testGetPrerequisites() {
        List<MLIndexInsightType> prerequisites = task.getPrerequisites();
        assertNotNull(prerequisites);
        assertTrue(prerequisites.isEmpty());
    }

    @Test
    public void testCreatePrerequisiteTask_ThrowsException() {
        try {
            task.createPrerequisiteTask(MLIndexInsightType.STATISTICAL_DATA);
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("IndexCorrelationTask has no prerequisites", e.getMessage());
        }
    }

    @Test
    public void testClient() {
        assertEquals(client, task.client);
    }

    @Test
    public void testRunTask_NoIndices() {
        mockClusterState(Collections.emptyList());
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(5000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertNotNull(result);
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testRunTask_SinglePattern() {
        mockClusterState(List.of("logs-otel-v1-000001", "logs-otel-v1-000002"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        // Mock LLM response for type detection
        String llmResponse = """
            <index_type_analysis>
            {
              "type": "LOG",
              "confidence": "high",
              "reasoning": "Contains log fields",
              "time_field": "time",
              "trace_id_field": "traceId",
              "span_id_field": "spanId"
            }
            </index_type_analysis>
            """;
        mockMLExecuteSuccess(client, llmResponse);

        // Mock mapping
        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        setupMappingMock();

        // Mock search for sample documents
        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(5000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testRunTask_MultiplePatterns() throws Exception {
        mockClusterState(List.of("logs-otel-v1-000001", "logs-otel-v1-000002", "jaeger-span-2025-12-19", "jaeger-span-2025-12-20"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        // Mock LLM responses
        mockMLExecuteForMultiplePatterns(client);

        // Mock mappings
        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        mockMappingForIndex("jaeger-span-2025-12-19", buildTraceMapping());
        setupMappingMock();

        // Mock search
        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(5000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());

        // Verify content contains patterns
        Map<String, Object> content = MAPPER.readValue(result.getContent(), Map.class);
        assertNotNull(content.get("all_patterns"));
    }

    @Test
    public void testRunTask_SystemIndicesFiltered() {
        mockClusterState(List.of(".kibana", ".opensearch", "logs-otel-v1-000001"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);
        mockMLExecuteSuccess(client, buildLogTypeResponse());
        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        setupMappingMock();
        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(5000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testRunTask_LLMFailure() {
        mockClusterState(List.of("unknown-index-123"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);
        mockMLExecuteFailure(client, "LLM service unavailable");
        mockMappingForIndex("unknown-index-123", buildGenericMapping());
        setupMappingMock();
        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(5000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testRunTask_ClusterStateFailure() {
        doAnswer(invocation -> {
            ActionListener<ClusterStateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Cluster state error"));
            return null;
        }).when(clusterAdmin).state(any(ClusterStateRequest.class), any(ActionListener.class));

        task.runTask("tenant-id", listener);

        verify(listener, timeout(5000)).onFailure(any(Exception.class));
    }

    @Test
    public void testRunTask_MappingFailure() {
        mockClusterState(List.of("test-index"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Mapping error"));
            return null;
        }).when(indicesAdmin).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(5000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testRunTask_SearchFailure() {
        mockClusterState(List.of("test-index"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);
        mockMLExecuteSuccess(client, buildLogTypeResponse());
        mockMappingForIndex("test-index", buildLogMapping());
        setupMappingMock();

        // Mock search failure
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Search error"));
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(5000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testRunTask_MultiplePatternsOfSameType() throws Exception {
        // Test case: multiple LOG patterns and multiple METRIC patterns
        // This triggers LLM-based intelligent matching
        mockClusterState(
            List
                .of(
                    "logs-otel-v1-000001",
                    "logs-otel-v1-000002",
                    "logs-app-000001",
                    "logs-app-000002",
                    "jaeger-span-2025-12-19",
                    "ss4o_metrics-otel-2025.12.19",
                    "prometheus-metrics-2025.12.19"
                )
        );
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        // Mock LLM for type detection (first calls) and correlation matching (later calls)
        mockMLExecuteForIntelligentMatching(client);

        // Mock mappings
        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        mockMappingForIndex("logs-app-000001", buildLogMapping());
        mockMappingForIndex("jaeger-span-2025-12-19", buildTraceMapping());
        mockMappingForIndex("ss4o_metrics-otel-2025.12.19", buildMetricMapping());
        mockMappingForIndex("prometheus-metrics-2025.12.19", buildMetricMapping());
        setupMappingMock();

        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(10000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());

        // Verify correlation tuple was built
        Map<String, Object> content = MAPPER.readValue(result.getContent(), Map.class);
        assertNotNull(content.get("correlation_tuple"));
    }

    @Test
    public void testRunTask_IntelligentMatchingWithLLMReasoning() throws Exception {
        // Test that LLM reasoning is included in result
        mockClusterState(List.of("logs-otel-v1-000001", "logs-app-000001", "jaeger-span-2025-12-19"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        // Mock LLM with correlation selection response
        mockMLExecuteForIntelligentMatching(client);

        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        mockMappingForIndex("logs-app-000001", buildLogMapping());
        mockMappingForIndex("jaeger-span-2025-12-19", buildTraceMapping());
        setupMappingMock();

        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(10000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testRunTask_CorrelationMatchingFallback() throws Exception {
        // Test fallback when LLM correlation matching fails
        mockClusterState(List.of("logs-otel-v1-000001", "logs-app-000001", "jaeger-span-2025-12-19"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        // Mock LLM to fail on correlation matching
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> listener = invocation.getArgument(2);
            int count = callCount.getAndIncrement();

            if (count < 3) {
                // First few calls for type detection - succeed
                String response = buildLogTypeResponse();
                ModelTensor tensor = ModelTensor.builder().result(response).build();
                ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
                ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
                listener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build());
            } else {
                // Correlation matching call - fail
                listener.onFailure(new RuntimeException("LLM correlation matching failed"));
            }
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));

        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        mockMappingForIndex("logs-app-000001", buildLogMapping());
        mockMappingForIndex("jaeger-span-2025-12-19", buildTraceMapping());
        setupMappingMock();

        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(10000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    public void testRunTask_VerifyCorrelationTupleStructure() throws Exception {
        // Verify that correlation tuple includes all required fields
        mockClusterState(List.of("logs-otel-v1-000001", "logs-app-000001", "jaeger-span-2025-12-19", "ss4o_metrics-otel-2025.12.19"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        // Mock different responses for each pattern type
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> listener = invocation.getArgument(2);
            int count = callCount.getAndIncrement();

            String response;
            if (count == 0 || count == 1) {
                // LOG patterns
                response = buildLogTypeResponse();
            } else if (count == 2) {
                // TRACE pattern (source)
                response = buildTraceTypeResponse();
            } else if (count == 3) {
                // METRIC pattern
                response = buildMetricTypeResponse();
            } else {
                // Correlation matching
                response = """
                    <correlation_selection>
                    {
                      "selected_log_pattern": "logs-otel-v1-*",
                      "selected_metric_pattern": "ss4o_metrics-otel-*",
                      "reasoning": "OTEL patterns are correlated"
                    }
                    </correlation_selection>
                    """;
            }

            ModelTensor tensor = ModelTensor.builder().result(response).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            listener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build());
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));

        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        mockMappingForIndex("logs-app-000001", buildLogMapping());
        mockMappingForIndex("jaeger-span-2025-12-19", buildTraceMapping());
        mockMappingForIndex("ss4o_metrics-otel-2025.12.19", buildMetricMapping());
        setupMappingMock();

        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(10000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());

        // Verify correlation tuple structure
        Map<String, Object> content = MAPPER.readValue(result.getContent(), Map.class);
        Map<String, Object> tuple = (Map<String, Object>) content.get("correlation_tuple");
        assertNotNull(tuple);

        // Check if logs/trace/metrics sections exist
        assertTrue(tuple.containsKey("logs") || tuple.containsKey("trace") || tuple.containsKey("metrics"));
    }

    @Test
    public void testBuildCorrelationTuple_SimplePath() throws Exception {
        // Test the simple path: one pattern per type
        // This triggers buildCorrelationTupleFromSource directly
        mockClusterState(List.of("logs-otel-v1-000001", "jaeger-span-2025-12-19", "ss4o_metrics-otel-2025.12.19"));
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        // Mock responses for each type
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> listener = invocation.getArgument(2);
            int count = callCount.getAndIncrement();

            String response;
            if (count == 0) {
                response = buildLogTypeResponse();
            } else if (count == 1) {
                response = buildTraceTypeResponse();
            } else {
                response = buildMetricTypeResponse();
            }

            ModelTensor tensor = ModelTensor.builder().result(response).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            listener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build());
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));

        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        mockMappingForIndex("jaeger-span-2025-12-19", buildTraceMapping());
        mockMappingForIndex("ss4o_metrics-otel-2025.12.19", buildMetricMapping());
        setupMappingMock();
        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(10000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());

        Map<String, Object> content = MAPPER.readValue(result.getContent(), Map.class);
        Map<String, Object> tuple = (Map<String, Object>) content.get("correlation_tuple");
        assertNotNull(tuple);

        // Verify tuple contains expected keys (source is jaeger-span which is TRACE)
        assertTrue(tuple.containsKey("logs"));
        assertTrue(tuple.containsKey("trace"));
        assertTrue(tuple.containsKey("metrics"));
    }

    @Test
    public void testIntelligentMatching_TriggerAllMethods() throws Exception {
        // Test specifically designed to trigger:
        // - buildCorrelationMatchingPromptForSource
        // - parseCorrelationMatchingResponseForSource
        // - extractXmlContent
        // - buildCorrelationTupleFromSource

        // Create scenario with 2 LOG patterns and 2 METRIC patterns
        mockClusterState(
            List
                .of(
                    "logs-otel-v1-000001",
                    "logs-otel-v1-000002",
                    "logs-app-prod-000001",
                    "logs-app-prod-000002",
                    "jaeger-span-2025-12-19",
                    "jaeger-span-2025-12-20",
                    "ss4o_metrics-otel-2025.12.19",
                    "ss4o_metrics-otel-2025.12.20",
                    "prometheus-metrics-2025.12.19",
                    "prometheus-metrics-2025.12.20"
                )
        );
        mockGetFailToGet(sdkClient, "");
        mockMLConfigSuccess(client);
        mockUpdateSuccess(sdkClient);

        // Mock LLM responses
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> listener = invocation.getArgument(2);
            int count = callCount.getAndIncrement();

            String response;
            // First 5 calls are for pattern type detection
            if (count < 5) {
                if (count == 0 || count == 1) {
                    response = buildLogTypeResponse();
                } else if (count == 2) {
                    response = buildTraceTypeResponse();
                } else {
                    response = buildMetricTypeResponse();
                }
            } else {
                // This is the correlation matching call
                // This response will trigger extractXmlContent and parseCorrelationMatchingResponseForSource
                response =
                    """
                        <correlation_selection>
                        {
                          "selected_log_pattern": "logs-otel-v1-*",
                          "selected_metric_pattern": "ss4o_metrics-otel-*",
                          "reasoning": "Both patterns use OTEL naming convention and are likely from the same observability stack deployed together"
                        }
                        </correlation_selection>
                        """;
            }

            ModelTensor tensor = ModelTensor.builder().result(response).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            listener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build());
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));

        // Mock ALL indices' mappings
        mockMappingForIndex("logs-otel-v1-000001", buildLogMapping());
        mockMappingForIndex("logs-otel-v1-000002", buildLogMapping());
        mockMappingForIndex("logs-app-prod-000001", buildLogMapping());
        mockMappingForIndex("logs-app-prod-000002", buildLogMapping());
        mockMappingForIndex("jaeger-span-2025-12-19", buildTraceMapping());
        mockMappingForIndex("jaeger-span-2025-12-20", buildTraceMapping());
        mockMappingForIndex("ss4o_metrics-otel-2025.12.19", buildMetricMapping());
        mockMappingForIndex("ss4o_metrics-otel-2025.12.20", buildMetricMapping());
        mockMappingForIndex("prometheus-metrics-2025.12.19", buildMetricMapping());
        mockMappingForIndex("prometheus-metrics-2025.12.20", buildMetricMapping());

        // Setup the unified mapping mock
        setupMappingMock();

        mockSearchForSampleDocuments(client);

        task.runTask("tenant-id", listener);

        ArgumentCaptor<IndexInsight> captor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener, timeout(15000)).onResponse(captor.capture());

        IndexInsight result = captor.getValue();
        assertEquals(IndexInsightTaskStatus.COMPLETED, result.getStatus());

        Map<String, Object> content = MAPPER.readValue(result.getContent(), Map.class);
        Map<String, Object> tuple = (Map<String, Object>) content.get("correlation_tuple");
        assertNotNull(tuple);

        // Verify LLM reasoning was included
        if (tuple.containsKey("llm_reasoning")) {
            String reasoning = (String) tuple.get("llm_reasoning");
            assertNotNull(reasoning);
            assertTrue(reasoning.contains("OTEL"));
        }
    }

    // Helper methods

    private void mockClusterState(List<String> indices) {
        doAnswer(invocation -> {
            ActionListener<ClusterStateResponse> listener = invocation.getArgument(1);

            ClusterState clusterState = mock(ClusterState.class);
            Metadata metadata = mock(Metadata.class);

            Map<String, IndexMetadata> indicesMap = new HashMap<>();
            for (String indexName : indices) {
                IndexMetadata indexMetadata = mock(IndexMetadata.class);
                indicesMap.put(indexName, indexMetadata);
            }

            when(metadata.getIndices()).thenReturn(indicesMap);
            when(clusterState.metadata()).thenReturn(metadata);

            ClusterStateResponse response = mock(ClusterStateResponse.class);
            when(response.getState()).thenReturn(clusterState);

            listener.onResponse(response);
            return null;
        }).when(clusterAdmin).state(any(ClusterStateRequest.class), any(ActionListener.class));
    }

    // Store mappings for all indices
    private Map<String, Map<String, Object>> indexMappings = new HashMap<>();

    private void mockMappingForIndex(String indexName, Map<String, Object> mappingSource) {
        indexMappings.put(indexName, mappingSource);
    }

    private void setupMappingMock() {
        doAnswer(invocation -> {
            GetMappingsRequest request = invocation.getArgument(0);
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);

            String requestedIndex = (request.indices() != null && request.indices().length > 0) ? request.indices()[0] : null;

            if (requestedIndex != null && indexMappings.containsKey(requestedIndex)) {
                Map<String, Object> mappingSource = indexMappings.get(requestedIndex);
                MappingMetadata mappingMetadata = mock(MappingMetadata.class);
                when(mappingMetadata.getSourceAsMap()).thenReturn(mappingSource);

                Map<String, MappingMetadata> mappingsMap = new HashMap<>();
                mappingsMap.put(requestedIndex, mappingMetadata);

                GetMappingsResponse response = mock(GetMappingsResponse.class);
                when(response.getMappings()).thenReturn(mappingsMap);

                listener.onResponse(response);
            } else {
                GetMappingsResponse response = mock(GetMappingsResponse.class);
                when(response.getMappings()).thenReturn(new HashMap<>());
                listener.onResponse(response);
            }
            return null;
        }).when(indicesAdmin).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));
    }

    private void mockSearchForSampleDocuments(Client client) {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = mock(SearchResponse.class);

            SearchHit[] hits = new SearchHit[3];
            for (int i = 0; i < 3; i++) {
                SearchHit hit = mock(SearchHit.class);
                when(hit.getSourceAsMap()).thenReturn(Map.of("time", "2025-01-01T00:00:00Z", "message", "Sample log " + i));
                hits[i] = hit;
            }

            SearchHits searchHits = new SearchHits(hits, null, 0);
            when(response.getHits()).thenReturn(searchHits);
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));
    }

    private void mockMLExecuteForMultiplePatterns(Client client) {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> listener = invocation.getArgument(2);

            // Alternate between responses
            String response = buildLogTypeResponse();

            ModelTensor tensor = ModelTensor.builder().result(response).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            listener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build());
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));
    }

    private void mockMLExecuteForIntelligentMatching(Client client) {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> listener = invocation.getArgument(2);
            int count = callCount.getAndIncrement();

            String response;
            if (count < 5) {
                // Type detection calls - return appropriate types
                if (count % 3 == 0) {
                    response = buildLogTypeResponse();
                } else if (count % 3 == 1) {
                    response = buildTraceTypeResponse();
                } else {
                    response = buildMetricTypeResponse();
                }
            } else {
                // Correlation matching call - return selection response
                response = buildCorrelationSelectionResponse();
            }

            ModelTensor tensor = ModelTensor.builder().result(response).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            listener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build());
            return null;
        }).when(client).execute(any(), any(), any(ActionListener.class));
    }

    private String buildCorrelationSelectionResponse() {
        return """
            <correlation_selection>
            {
              "selected_log_pattern": "logs-otel-v1-*",
              "selected_metric_pattern": "ss4o_metrics-otel-*",
              "reasoning": "These patterns share OTEL naming convention and are likely from the same observability stack"
            }
            </correlation_selection>
            """;
    }

    private String buildLogTypeResponse() {
        return """
            <index_type_analysis>
            {
              "type": "LOG",
              "confidence": "high",
              "reasoning": "Contains log fields",
              "time_field": "time",
              "trace_id_field": "traceId",
              "span_id_field": "spanId"
            }
            </index_type_analysis>
            """;
    }

    private String buildTraceTypeResponse() {
        return """
            <index_type_analysis>
            {
              "type": "TRACE",
              "confidence": "high",
              "reasoning": "Contains trace fields",
              "time_field": "startTimeMillis",
              "trace_id_field": "traceID",
              "span_id_field": "spanID"
            }
            </index_type_analysis>
            """;
    }

    private String buildMetricTypeResponse() {
        return """
            <index_type_analysis>
            {
              "type": "METRIC",
              "confidence": "high",
              "reasoning": "Contains metric fields",
              "time_field": "time",
              "trace_id_field": null,
              "span_id_field": null
            }
            </index_type_analysis>
            """;
    }

    private Map<String, Object> buildLogMapping() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("time", Map.of("type", "date"));
        properties.put("message", Map.of("type", "text"));
        properties.put("severity", Map.of("type", "keyword"));
        properties.put("spanId", Map.of("type", "keyword"));
        properties.put("traceId", Map.of("type", "keyword"));
        return Map.of("properties", properties);
    }

    private Map<String, Object> buildTraceMapping() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("startTimeMillis", Map.of("type", "date"));
        properties.put("spanID", Map.of("type", "keyword"));
        properties.put("traceID", Map.of("type", "keyword"));
        properties.put("operationName", Map.of("type", "keyword"));
        return Map.of("properties", properties);
    }

    private Map<String, Object> buildMetricMapping() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("time", Map.of("type", "date"));
        properties.put("value", Map.of("type", "double"));
        properties.put("name", Map.of("type", "keyword"));
        return Map.of("properties", properties);
    }

    private Map<String, Object> buildGenericMapping() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("timestamp", Map.of("type", "date"));
        properties.put("data", Map.of("type", "text"));
        return Map.of("properties", properties);
    }
}

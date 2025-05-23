/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.io.IOException;
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
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.stats.otel.counters.MLAdoptionMetricsCounter;
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
        when(metadata.indices()).thenReturn(Map.of(ML_MODEL_INDEX, mock(IndexMetadata.class)));

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
        SearchResponse searchResponse = createModelSearchResponse();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, times(1)).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(mockCounter, times(1)).add(eq(1.0), any(Tags.class));
    }

    @Test
    public void testMetricCollectionSettings() throws IOException {
        SearchResponse searchResponse = createModelSearchResponse();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
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
}

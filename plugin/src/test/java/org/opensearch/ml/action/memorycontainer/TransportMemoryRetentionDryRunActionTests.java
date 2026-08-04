/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.jobs.processors.MemoryRetentionJobProcessor;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportMemoryRetentionDryRunActionTests extends OpenSearchTestCase {

    private TransportMemoryRetentionDryRunAction action;

    @Mock
    private Client client;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private MemoryContainerHelper memoryContainerHelper;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private TransportService transportService;
    @Mock
    private Task task;
    @Mock
    private ActionListener<MLMemoryRetentionDryRunResponse> listener;

    @Captor
    private ArgumentCaptor<MLMemoryRetentionDryRunResponse> responseCaptor;
    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private ThreadContext threadContext;

    private static final String CONTAINER_JSON = "{\"name\":\"c\",\"configuration\":{\"index_prefix\":\"p\",\"use_system_index\":false}}";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        MemoryRetentionJobProcessor.reset();

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        lenient().when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_enabled", true)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.default_session_retention_days", -1)
            .put("plugins.ml_commons.memory.default_session_max_count", -1)
            .put("plugins.ml_commons.memory.default_long_term_max_count", -1)
            .put("plugins.ml_commons.memory.default_history_max_count", -1)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .put("plugins.ml_commons.memory.working_memory_ttl_days", 30)
            .build();
        java.util.Set<Setting<?>> set = new java.util.HashSet<>(
            java.util.Arrays
                .asList(
                    MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED,
                    MLCommonsSettings.ML_COMMONS_MEMORY_RETENTION_ENABLED,
                    MLCommonsSettings.ML_COMMONS_MEMORY_RETENTION_JOB_THROTTLE_SECONDS,
                    MLCommonsSettings.ML_COMMONS_MEMORY_DEFAULT_SESSION_RETENTION_DAYS,
                    MLCommonsSettings.ML_COMMONS_MEMORY_DEFAULT_SESSION_MAX_COUNT,
                    MLCommonsSettings.ML_COMMONS_MEMORY_DEFAULT_LONG_TERM_MAX_COUNT,
                    MLCommonsSettings.ML_COMMONS_MEMORY_DEFAULT_HISTORY_MAX_COUNT,
                    MLCommonsSettings.ML_COMMONS_MEMORY_ORPHAN_TTL_DAYS,
                    MLCommonsSettings.ML_COMMONS_MEMORY_WORKING_MEMORY_TTL_DAYS
                )
        );
        lenient().when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, set));
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        lenient().when(clusterService.state()).thenReturn(clusterState);
        lenient().when(clusterState.metadata()).thenReturn(metadata);
        lenient().when(metadata.hasIndex(anyString())).thenReturn(true);

        action = new TransportMemoryRetentionDryRunAction(
            transportService,
            actionFilters,
            client,
            clusterService,
            threadPool,
            xContentRegistry,
            mlFeatureEnabledSetting,
            memoryContainerHelper
        );
    }

    private GetResponse existsGet(boolean exists, String json) {
        GetResponse gr = mock(GetResponse.class);
        when(gr.isExists()).thenReturn(exists);
        if (exists) {
            lenient().when(gr.getSourceAsString()).thenReturn(json);
            lenient().when(gr.getSourceAsMap()).thenReturn(java.util.Map.of());
        }
        return gr;
    }

    public void testForbiddenWhenAgenticMemoryDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId("c1").build();
        action.doExecute(task, request, listener);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exceptionCaptor.getValue()).status());
    }

    public void testSingleContainerNotFound() {
        doAnswer(inv -> {
            ActionListener<GetResponse> l = inv.getArgument(1);
            l.onResponse(existsGet(false, null));
            return null;
        }).when(client).get(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId("missing").build();
        action.doExecute(task, request, listener);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) exceptionCaptor.getValue()).status());
    }

    public void testSingleContainerIndexNotFound() {
        doAnswer(inv -> {
            ActionListener<GetResponse> l = inv.getArgument(1);
            l.onFailure(new IndexNotFoundException("no index"));
            return null;
        }).when(client).get(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId("c1").build();
        action.doExecute(task, request, listener);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) exceptionCaptor.getValue()).status());
    }

    public void testSingleContainerAccessDenied() {
        doAnswer(inv -> {
            ActionListener<GetResponse> l = inv.getArgument(1);
            l.onResponse(existsGet(true, CONTAINER_JSON));
            return null;
        }).when(client).get(any(), isA(ActionListener.class));
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(false);

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId("c1").build();
        action.doExecute(task, request, listener);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exceptionCaptor.getValue()).status());
    }

    public void testSingleContainerSuccessNoPolicy() {
        // Container has no retention policy and no cluster defaults -> policy_source none, no deletes, single result.
        doAnswer(inv -> {
            ActionListener<GetResponse> l = inv.getArgument(1);
            l.onResponse(existsGet(true, CONTAINER_JSON));
            return null;
        }).when(client).get(any(), isA(ActionListener.class));
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        // Any incidental search returns empty counts.
        lenient().doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(emptyCount());
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId("c1").build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        MLMemoryRetentionDryRunResponse response = responseCaptor.getValue();
        assertFalse(response.isClusterWide());
        assertEquals(1, response.getResults().size());
        assertEquals("none", response.getResults().get(0).getPolicySource());
        assertEquals(0, response.getResults().get(0).getTotalWouldDelete());
    }

    public void testClusterWideEmpty() {
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(emptyHits());
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        assertTrue(responseCaptor.getValue().isClusterWide());
        assertEquals(0, responseCaptor.getValue().getResults().size());
    }

    public void testClusterWideSkipsUnparseableContainerReportsSkippedCount() {
        // A container whose stored source cannot be parsed is dropped from the results and counted in skipped_count.
        SearchHit badHit = new SearchHit(0, "bad-container", null, null);
        badHit.sourceRef(new org.opensearch.core.common.bytes.BytesArray("not-json"));
        SearchResponse r = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[] { badHit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(r.getHits()).thenReturn(hits);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(r);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        MLMemoryRetentionDryRunResponse response = responseCaptor.getValue();
        assertTrue(response.isClusterWide());
        assertEquals(0, response.getResults().size());
        assertEquals(1, response.getSkippedCount());
    }

    public void testClusterWideIndexNotFoundReturnsEmptyArray() {
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onFailure(new IndexNotFoundException("no container index"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        assertTrue(responseCaptor.getValue().isClusterWide());
        assertEquals(0, responseCaptor.getValue().getResults().size());
    }

    private SearchResponse emptyCount() {
        SearchResponse r = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(r.getHits()).thenReturn(hits);
        return r;
    }

    private SearchResponse emptyHits() {
        return emptyCount();
    }
}

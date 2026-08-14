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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
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
import org.opensearch.search.DocValueFormat;
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
        // dryRunContainer consults the node-scoped remote-metadata setting via clusterService.getSettings();
        // no remote store is configured here (REMOTE_METADATA_TYPE defaults to empty), so isRemoteMetadataStore == false.
        lenient().when(clusterService.getSettings()).thenReturn(settings);
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

    public void testClusterWideTruncatesAtContainerCap() {
        // Finding #2: the cluster-wide scan is bounded at MAX_CONTAINERS_PER_DRY_RUN. Simulate an effectively unbounded
        // supply of containers by having every container-index page return a full page (CONTAINER_PAGE_SIZE hits) with
        // distinct search-after sort values. The drain must stop after exactly the cap and flag the response truncated
        // with a warning, rather than silently dropping the remaining containers or scanning them all.
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        AtomicInteger nextContainerOffset = new AtomicInteger(0);
        doAnswer(inv -> {
            SearchRequest req = inv.getArgument(0);
            ActionListener<SearchResponse> l = inv.getArgument(1);
            if (req.indices().length > 0 && ".plugins-ml-am-memory-container".equals(req.indices()[0])) {
                // Container listing page: always a full page so nextPageSort is non-null (more pages remain).
                int base = nextContainerOffset.getAndAdd(100);
                SearchHit[] pageHits = new SearchHit[100];
                for (int i = 0; i < 100; i++) {
                    String id = "container-" + (base + i);
                    SearchHit hit = new SearchHit(base + i, id, null, null);
                    hit.sourceRef(new org.opensearch.core.common.bytes.BytesArray(CONTAINER_JSON));
                    hit.sortValues(new Object[] { id }, new DocValueFormat[] { DocValueFormat.RAW });
                    pageHits[i] = hit;
                }
                SearchResponse resp = mock(SearchResponse.class);
                SearchHits hits = new SearchHits(pageHits, new TotalHits(100000, TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), Float.NaN);
                when(resp.getHits()).thenReturn(hits);
                l.onResponse(resp);
            } else {
                // Per-container counting search: no matches.
                l.onResponse(emptyCount());
            }
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        MLMemoryRetentionDryRunResponse response = responseCaptor.getValue();
        assertTrue(response.isClusterWide());
        assertEquals(TransportMemoryRetentionDryRunAction.MAX_CONTAINERS_PER_DRY_RUN, response.getResults().size());
        assertTrue("response should be flagged truncated when the container cap is hit", response.isTruncated());
        assertNotNull("a truncation warning must be surfaced", response.getWarning());
        assertTrue(response.getWarning().contains(String.valueOf(TransportMemoryRetentionDryRunAction.MAX_CONTAINERS_PER_DRY_RUN)));
    }

    public void testClusterWideMultiTenancyDoesNotLeakNullTenantContainer() {
        // Finding #4: with multi-tenancy enabled, a container whose stored tenantId is null (pre-multi-tenancy stamp)
        // must NOT be returned to a caller scoped to a concrete tenant. Objects.equals(tenantId, null) is false, so the
        // container is silently skipped (not evaluated, not returned) — matching the single-container isolation path.
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        // CONTAINER_JSON carries no tenant_id -> container.getTenantId() == null.
        SearchHit hit = new SearchHit(0, "null-tenant-container", null, null);
        hit.sourceRef(new org.opensearch.core.common.bytes.BytesArray(CONTAINER_JSON));
        hit.sortValues(new Object[] { "null-tenant-container" }, new DocValueFormat[] { DocValueFormat.RAW });
        SearchResponse page = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(page.getHits()).thenReturn(hits);
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(page);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().tenantId("tenant-1").build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        MLMemoryRetentionDryRunResponse response = responseCaptor.getValue();
        assertTrue(response.isClusterWide());
        assertEquals("null-tenant container must not leak to a concrete-tenant caller", 0, response.getResults().size());
        // Tenant filtering is a silent skip, not a parse/eval failure, so it is not counted in skipped_count.
        assertEquals(0, response.getSkippedCount());
    }

    public void testSingleContainerTenantValidationFailure() {
        // Multi-tenancy on + null tenantId -> validateTenantId short-circuits with FORBIDDEN before any get().
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId("c1").build();
        action.doExecute(task, request, listener);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exceptionCaptor.getValue()).status());
    }

    public void testSingleContainerGetFailureWrappedAsStatus() {
        // A non-IndexNotFound get() failure is wrapped and surfaced (hits the log.error/wrapAsStatusException branch).
        doAnswer(inv -> {
            ActionListener<GetResponse> l = inv.getArgument(1);
            l.onFailure(new RuntimeException("boom"));
            return null;
        }).when(client).get(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId("c1").build();
        action.doExecute(task, request, listener);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof OpenSearchStatusException);
    }

    public void testClusterWideWithContainersEvaluatesEach() {
        // Two visible containers with no policy -> both evaluated, both produce a "none" result. Exercises the
        // full cluster-wide processHitChain path (searchContainerPage -> processHitChain -> dryRunContainer).
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(containerHits("a", "b"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        MLMemoryRetentionDryRunResponse response = responseCaptor.getValue();
        assertTrue(response.isClusterWide());
        assertEquals(2, response.getResults().size());
        assertEquals("none", response.getResults().get(0).getPolicySource());
    }

    public void testClusterWideSkipsAccessDeniedContainers() {
        // Access denied on every container -> all silently skipped, empty results but still cluster-wide.
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(containerHits("a", "b"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(false);

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        assertTrue(responseCaptor.getValue().isClusterWide());
        assertEquals(0, responseCaptor.getValue().getResults().size());
    }

    public void testClusterWideSkipsUnparseableContainers() {
        // A hit whose source is not valid container JSON is skipped without failing the whole run.
        SearchHit good = containerHit("a", CONTAINER_JSON);
        SearchHit bad = containerHit("b", "{not valid json");
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(hitsOf(good, bad));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        assertEquals(1, responseCaptor.getValue().getResults().size());
    }

    public void testClusterWideSearchErrorSurfaces() {
        // A non-IndexNotFound search failure is surfaced as a status exception.
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onFailure(new RuntimeException("search boom"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof OpenSearchStatusException);
    }

    public void testClusterWidePaginatesAcrossPages() {
        // First page returns a full page (100 hits) so a follow-up searchContainerPage runs with searchAfter;
        // second page is empty and terminates. Exercises the nextPageSort / searchAfter branch.
        SearchHit[] full = new SearchHit[100];
        for (int i = 0; i < 100; i++) {
            full[i] = containerHit("c" + i, CONTAINER_JSON);
        }
        java.util.concurrent.atomic.AtomicInteger call = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            if (call.getAndIncrement() == 0) {
                l.onResponse(hitsOf(full));
            } else {
                l.onResponse(emptyHits());
            }
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);

        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().build();
        action.doExecute(task, request, listener);

        verify(listener).onResponse(responseCaptor.capture());
        assertEquals(100, responseCaptor.getValue().getResults().size());
        // 1 first page + 1 empty follow-up page.
        assertEquals(2, call.get());
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

    /** Builds a SearchHit carrying the given id and JSON source, with a sort value so pagination can read it. */
    private SearchHit containerHit(String id, String json) {
        SearchHit hit = new SearchHit(0, id, null, java.util.Collections.emptyMap());
        hit.sourceRef(new org.opensearch.core.common.bytes.BytesArray(json));
        hit.sortValues(new Object[] { id }, new org.opensearch.search.DocValueFormat[] { org.opensearch.search.DocValueFormat.RAW });
        return hit;
    }

    private SearchResponse hitsOf(SearchHit... hits) {
        SearchResponse r = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(hits, new TotalHits(hits.length, TotalHits.Relation.EQUAL_TO), 1.0f);
        when(r.getHits()).thenReturn(searchHits);
        return r;
    }

    private SearchResponse containerHits(String... ids) {
        SearchHit[] hits = new SearchHit[ids.length];
        for (int i = 0; i < ids.length; i++) {
            hits[i] = containerHit(ids[i], CONTAINER_JSON);
        }
        return hitsOf(hits);
    }
}

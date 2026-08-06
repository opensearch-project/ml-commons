/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ORPHAN_SWEEP_BASELINE_TIME_FIELD;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

@RunWith(MockitoJUnitRunner.class)
public class MemoryRetentionJobProcessorTests {

    @Rule
    public Timeout globalTimeout = new Timeout(60, TimeUnit.SECONDS);

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    private ThreadContext threadContext;

    private MemoryRetentionJobProcessor processor;

    @Before
    public void setUp() {
        MemoryRetentionJobProcessor.reset();

        // Default settings: multi-tenancy disabled, throttle = 1 second
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
        when(clusterService.getSettings()).thenReturn(settings);

        java.util.Set<Setting<?>> clusterSettingsSet = new java.util.HashSet<>(
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
        ClusterSettings clusterSettings = new ClusterSettings(settings, clusterSettingsSet);
        lenient().when(clusterService.getClusterSettings()).thenReturn(clusterSettings);

        // Default cluster state: all memory indices exist (orphan sweep runs)
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        lenient().when(clusterService.state()).thenReturn(clusterState);
        lenient().when(clusterState.metadata()).thenReturn(metadata);
        lenient().when(metadata.hasIndex(anyString())).thenReturn(true);

        // Use real ThreadContext (it's a final class and cannot be mocked)
        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Mock threadPool.schedule to execute immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(threadPool).schedule(any(Runnable.class), any(TimeValue.class), anyString());

        processor = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
    }

    @Test
    public void testGetInstance() {
        MemoryRetentionJobProcessor instance1 = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
        MemoryRetentionJobProcessor instance2 = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
        assertSame(instance1, instance2);
    }

    @Test
    public void testRunSkipsWhenMultiTenancyEnabled() {
        Settings settings = Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", true).build();
        when(clusterService.getSettings()).thenReturn(settings);
        java.util.Set<Setting<?>> s = new java.util.HashSet<>(
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
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, s));

        processor.run();

        // Should not search for containers when multi-tenancy is enabled
        verify(client, never()).search(any(SearchRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testRunSkipsWhenRetentionDisabled() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_enabled", false)
            .build();
        when(clusterService.getSettings()).thenReturn(settings);
        java.util.Set<Setting<?>> s = new java.util.HashSet<>(
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
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, s));

        processor.run();

        // Should not search for containers when retention is disabled
        verify(client, never()).search(any(SearchRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testRunExecutesWhenMultiTenancyDisabled() {
        // Return empty containers
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify container search is initiated
        verify(client, atLeastOnce()).search(any(SearchRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testRunExecutesWithDefaultSettings() {
        // retention_enabled defaults to false, so enable it explicitly; all other settings stay at their defaults.
        Settings defaultSettings = Settings.builder().put("plugins.ml_commons.memory.retention_enabled", true).build();
        when(clusterService.getSettings()).thenReturn(defaultSettings);
        java.util.Set<Setting<?>> s = new java.util.HashSet<>(
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
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(defaultSettings, s));

        // Return empty containers
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Default multi_tenancy_enabled is false, so job should execute
        verify(client, atLeastOnce()).search(any(SearchRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testNoContainersWithPolicy() {
        // Container search returns empty hits -> job completes
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(emptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).search(any(SearchRequest.class), isA(ActionListener.class));
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testContainerWithSessionRetentionTimeBased() {
        // 1 container with retention_days=7, mock 3 expired sessions -> verify cascade + bulk delete
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-time",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Session search returns 3 expired sessions with old last_updated_time
                long oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                SearchHit hit1 = createHitWithSource("expired-session-1", Map.of("last_updated_time", oldTimestamp));
                SearchHit hit2 = createHitWithSource("expired-session-2", Map.of("last_updated_time", oldTimestamp));
                SearchHit hit3 = createHitWithSource("expired-session-3", Map.of("last_updated_time", oldTimestamp));
                SearchHits sessionHits = new SearchHits(
                    new SearchHit[] { hit1, hit2, hit3 },
                    new TotalHits(3, TotalHits.Relation.EQUAL_TO),
                    Float.NaN
                );
                SearchResponse sessionResponse = mock(SearchResponse.class);
                when(sessionResponse.getHits()).thenReturn(sessionHits);
                listener.onResponse(sessionResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock cascade delete for working memory (Phase 2)
        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(10L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for sessions (Phase 3)
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify cascade delete was called on working memory
        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        // Verify bulk delete was called on sessions
        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testContainerWithSessionRetentionCountBased() {
        // max_count=5, 10 sessions -> verify 5 oldest deleted
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-count",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", null, 5)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger sessionSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = sessionSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Pinned count check (fire-and-forget): return 0 pinned docs
                    SearchResponse pinnedResp = mock(SearchResponse.class);
                    SearchHits pinnedHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(pinnedResp.getHits()).thenReturn(pinnedHits);
                    listener.onResponse(pinnedResp);
                } else if (searchNum == 1) {
                    // Count query: return totalHits=10 (exceeds max_count=5)
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(10, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else if (searchNum == 2) {
                    // Evict the 5 least-recently-updated excess sessions (LRU): sort must be last_updated_time
                    String sourceStr = request.source().toString();
                    assertTrue("Session count-based eviction must sort by last_updated_time", sourceStr.contains("last_updated_time"));
                    assertTrue("Session count-based eviction must NOT sort by created_time", !sourceStr.contains("created_time"));
                    SearchHit[] hits = new SearchHit[5];
                    for (int i = 0; i < 5; i++) {
                        hits[i] = new SearchHit(i, "session-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "session-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits sessionHits = new SearchHits(hits, new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse sessionResponse = mock(SearchResponse.class);
                    when(sessionResponse.getHits()).thenReturn(sessionHits);
                    listener.onResponse(sessionResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock cascade delete for working memory
        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(5L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for sessions
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify cascade delete + bulk delete both called
        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testSessionMaxCountShortCircuitsWhenUnderLimit() {
        // max_count=5, only 3 non-pinned sessions -> count query short-circuits: no ID search, no deletes
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-count-under",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", null, 5)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger sessionSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = sessionSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Pinned count check (fire-and-forget): return 0 pinned docs
                    SearchResponse pinnedResp = mock(SearchResponse.class);
                    SearchHits pinnedHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(pinnedResp.getHits()).thenReturn(pinnedHits);
                    listener.onResponse(pinnedResp);
                } else {
                    // Count query: return totalHits=3 (under max_count=5)
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(3, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Only the pinned check and the count query should have hit the session index
        assertTrue("Only pinned check + count query should fire when under max_count", sessionSearchCount.get() == 2);
        // No deletions since totalCount (3) <= max_count (5)
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testCascadeOrdering() {
        // Verify delete_by_query (working memory) is called BEFORE bulk delete (sessions)
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-cascade",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Session search returns expired sessions with old timestamps
                long oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                SearchHit hit1 = createHitWithSource("session-1", Map.of("last_updated_time", oldTimestamp));
                SearchHit hit2 = createHitWithSource("session-2", Map.of("last_updated_time", oldTimestamp));
                SearchHits sessionHits = new SearchHits(
                    new SearchHit[] { hit1, hit2 },
                    new TotalHits(2, TotalHits.Relation.EQUAL_TO),
                    Float.NaN
                );
                SearchResponse sessionResponse = mock(SearchResponse.class);
                when(sessionResponse.getHits()).thenReturn(sessionHits);
                listener.onResponse(sessionResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Track the ordering of operations
        AtomicInteger orderCounter = new AtomicInteger(0);
        AtomicInteger cascadeDeleteOrder = new AtomicInteger(-1);
        AtomicInteger bulkDeleteOrder = new AtomicInteger(-1);

        // Mock cascade delete-by-query for working memory (Phase 2)
        BulkByScrollResponse workingDeleteResponse = mock(BulkByScrollResponse.class);
        when(workingDeleteResponse.getDeleted()).thenReturn(5L);

        doAnswer(invocation -> {
            cascadeDeleteOrder.set(orderCounter.getAndIncrement());
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(workingDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for sessions (Phase 3)
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            bulkDeleteOrder.set(orderCounter.getAndIncrement());
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Working memory delete (cascade) must happen BEFORE session delete (bulk)
        assertTrue(
            "Working memory cascade delete should execute before session bulk delete",
            cascadeDeleteOrder.get() < bulkDeleteOrder.get()
        );
        assertTrue("Cascade delete should have been called", cascadeDeleteOrder.get() >= 0);
        assertTrue("Bulk delete should have been called", bulkDeleteOrder.get() >= 0);
    }

    @Test
    public void testPinnedSessionsExcluded() {
        // Pinned sessions older than retention_days -> not deleted
        // The processor's query uses mustNot(pinned=true), so we verify the query contains pinned filter
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-pinned",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Verify the query excludes pinned=true
                String queryString = request.source().query().toString();
                assertTrue("Query should exclude pinned documents (must_not pinned:true)", queryString.contains("pinned"));

                // Return empty result - all sessions are pinned and excluded
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify session search was made (with pinned exclusion)
        verify(client, atLeast(2)).search(any(SearchRequest.class), isA(ActionListener.class));
        // No deletions should occur since all sessions are pinned (excluded by query)
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testBatching() {
        // 600 expired sessions -> verify 2 delete_by_query calls (500 + 100) for working memory cascade
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-batch",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger sessionPageCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Return 600 expired sessions across 6 full pages of 100, then an empty page
                long oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                int pageNum = sessionPageCount.getAndIncrement();
                if (pageNum < 6) {
                    // Full page of 100 hits (triggers pagination)
                    SearchHit[] hits = new SearchHit[100];
                    for (int i = 0; i < 100; i++) {
                        int globalIdx = pageNum * 100 + i;
                        hits[i] = createHitWithSource("session-" + globalIdx, Map.of("last_updated_time", oldTimestamp));
                        hits[i].sortValues(new Object[] { "session-" + globalIdx }, new DocValueFormat[] { DocValueFormat.RAW });
                    }
                    SearchHits sessionHits = new SearchHits(hits, new TotalHits(600, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse sessionResponse = mock(SearchResponse.class);
                    when(sessionResponse.getHits()).thenReturn(sessionHits);
                    listener.onResponse(sessionResponse);
                } else {
                    // Final page: empty (terminates pagination)
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Track cascade delete calls
        AtomicInteger cascadeDeleteCount = new AtomicInteger(0);

        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(100L);

        doAnswer(invocation -> {
            cascadeDeleteCount.incrementAndGet();
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // With 600 sessions and DELETE_BY_QUERY_BATCH_SIZE=500, expect 2 cascade delete calls
        assertTrue("Should have at least 2 cascade delete-by-query calls for 600 sessions (batch size 500)", cascadeDeleteCount.get() >= 2);
    }

    @Test
    public void testEmptyExpiredSet() {
        // No sessions match retention criteria -> phases 2-3 skipped
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-empty",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // No expired sessions found
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify no cascade delete or bulk delete was called
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testContainerProcessingError() {
        // One container fails -> next container still processed
        String sourceJson1 = "{\"configuration\":{\"index_prefix\":\"prefix1\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";
        String sourceJson2 = "{\"configuration\":{\"index_prefix\":\"prefix2\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchHit hit1 = new SearchHit(0, "container-fail", null, null);
        hit1.sourceRef(new BytesArray(sourceJson1));
        hit1.sortValues(new Object[] { "container-fail" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchHit hit2 = new SearchHit(1, "container-success", null, null);
        hit2.sourceRef(new BytesArray(sourceJson2));
        hit2.sortValues(new Object[] { "container-success" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchResponse containerSearchResponse = mock(SearchResponse.class);
        // Less than page size (100) so no pagination
        SearchHits containerHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(containerSearchResponse.getHits()).thenReturn(containerHits);

        AtomicInteger sessionSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                listener.onResponse(containerSearchResponse);
            } else {
                int count = sessionSearchCount.getAndIncrement();
                if (count == 0) {
                    // First container's session search fails
                    listener.onFailure(new RuntimeException("Simulated search failure"));
                } else {
                    // Second container's session search succeeds with empty results
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify that we searched at least twice (once for each container's sessions)
        assertTrue("Both containers should be processed, second container search made", sessionSearchCount.get() >= 2);
        // Neither container deleted anything (first failed, second had no expired data) -> no throttle
        verify(threadPool, never()).schedule(any(Runnable.class), any(TimeValue.class), anyString());
    }

    @Test
    public void testNoOpContainerSkipsThrottle() {
        // Container with a policy but no expired data -> no deletions -> no inter-container throttle
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-noop",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // No expired sessions found
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Job still advances to the next step (orphan sweep container search) without any throttle
        assertTrue("Job should proceed past the no-op container", containerSearchCount.get() >= 2);
        verify(threadPool, never()).schedule(any(Runnable.class), any(TimeValue.class), anyString());
    }

    @Test
    public void testContainerWithDeletionsSchedulesThrottle() {
        // Container with expired sessions -> deletions occur -> throttle scheduled before next container
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-throttled",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                long oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                SearchHit expiredHit = createHitWithSource("expired-session-1", Map.of("last_updated_time", oldTimestamp));
                SearchHits sessionHits = new SearchHits(
                    new SearchHit[] { expiredHit },
                    new TotalHits(1, TotalHits.Relation.EQUAL_TO),
                    Float.NaN
                );
                SearchResponse sessionResponse = mock(SearchResponse.class);
                when(sessionResponse.getHits()).thenReturn(sessionHits);
                listener.onResponse(sessionResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock cascade delete for working memory
        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(2L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for sessions
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Throttle (retention_job_throttle_seconds=1) is scheduled after the container with deletions
        verify(threadPool, atLeastOnce()).schedule(any(Runnable.class), eq(TimeValue.timeValueSeconds(1)), anyString());
    }

    @Test
    public void testNullSessionIndex() {
        // Container with disableSession=true -> session retention skipped, but working memory TTL fires (Phase 6)
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-no-session", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query for Phase 6 working memory TTL (disableSession=true triggers it)
        BulkByScrollResponse ttlResponse = mock(BulkByScrollResponse.class);
        when(ttlResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ttlResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        // Session retention is skipped (session index is null), but Phase 6 working memory TTL fires
        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        // No bulk deletes (no sessions to delete, no count-based eviction)
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testMultipleContainersProcessed() {
        // 2 containers -> both processed sequentially
        String sourceJson1 = "{\"configuration\":{\"index_prefix\":\"prefix1\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";
        String sourceJson2 = "{\"configuration\":{\"index_prefix\":\"prefix2\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":30}}}}";

        SearchHit hit1 = new SearchHit(0, "container-1", null, null);
        hit1.sourceRef(new BytesArray(sourceJson1));
        hit1.sortValues(new Object[] { "container-1" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchHit hit2 = new SearchHit(1, "container-2", null, null);
        hit2.sourceRef(new BytesArray(sourceJson2));
        hit2.sortValues(new Object[] { "container-2" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchResponse containerSearchResponse = mock(SearchResponse.class);
        SearchHits containerHits = new SearchHits(new SearchHit[] { hit1, hit2 }, new TotalHits(2, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(containerSearchResponse.getHits()).thenReturn(containerHits);

        AtomicInteger sessionSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                listener.onResponse(containerSearchResponse);
            } else {
                sessionSearchCount.incrementAndGet();
                // Return 1 expired session for each container with old timestamp
                long oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
                SearchHit expiredHit = createHitWithSource(
                    "expired-session-" + sessionSearchCount.get(),
                    Map.of("last_updated_time", oldTimestamp)
                );
                SearchHits sessionHits = new SearchHits(
                    new SearchHit[] { expiredHit },
                    new TotalHits(1, TotalHits.Relation.EQUAL_TO),
                    Float.NaN
                );
                SearchResponse sessionResponse = mock(SearchResponse.class);
                when(sessionResponse.getHits()).thenReturn(sessionHits);
                listener.onResponse(sessionResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock cascade delete
        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(1L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Both containers should have been processed: at least 2 session searches
        assertTrue("Both containers should be processed with session searches", sessionSearchCount.get() >= 2);
        // Verify cascade delete was called for both containers
        verify(client, atLeast(2)).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        // Verify bulk delete was called for both containers
        verify(client, atLeast(2)).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    // --- Phase 4: Long-Term Retention Tests ---

    @Test
    public void testLongTermRetentionDays() {
        // Container with LTM policy retention_days=30 -> verify _delete_by_query on long-term index
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"retention_days\":30}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ltm-days", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // No expired sessions (no session retention rule)
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query for long-term retention_days
        BulkByScrollResponse ltmDeleteResponse = mock(BulkByScrollResponse.class);
        when(ltmDeleteResponse.getDeleted()).thenReturn(5L);

        doAnswer(invocation -> {
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            // Verify the query targets long-term index and uses last_updated_time
            String queryString = dbq.getSearchRequest().source().query().toString();
            assertTrue("Should filter by memory_container_id", queryString.contains("memory_container_id"));
            assertTrue("Should use last_updated_time for long-term", queryString.contains("last_updated_time"));
            assertTrue("Should exclude pinned docs", queryString.contains("pinned"));

            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ltmDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testLongTermMaxCount() {
        // Container with LTM policy max_count=100, 150 docs -> verify search for oldest 50 IDs + bulk delete
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"max_count\":100}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ltm-count", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Pinned count check (fire-and-forget): return 0 pinned docs
                    SearchResponse pinnedResp = mock(SearchResponse.class);
                    SearchHits pinnedHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(pinnedResp.getHits()).thenReturn(pinnedHits);
                    listener.onResponse(pinnedResp);
                } else if (searchNum == 1) {
                    // Count query: return totalHits=150
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(150, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    // Evict the 50 least-recently-updated excess docs (LRU): sort must be last_updated_time
                    String sourceStr = request.source().toString();
                    assertTrue("Long-term count-based eviction must sort by last_updated_time", sourceStr.contains("last_updated_time"));
                    assertTrue("Long-term count-based eviction must NOT sort by created_time", !sourceStr.contains("created_time"));
                    // Search for oldest 50 IDs: return 50 hits
                    SearchHit[] hits = new SearchHit[50];
                    for (int i = 0; i < 50; i++) {
                        hits[i] = new SearchHit(i, "ltm-doc-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "ltm-doc-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits searchHits = new SearchHits(hits, new TotalHits(50, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse searchResp = mock(SearchResponse.class);
                    when(searchResp.getHits()).thenReturn(searchHits);
                    listener.onResponse(searchResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Verify bulk delete called for the 50 excess docs
        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testLongTermPinnedExclusion() {
        // Verify mustNot(pinned=true) in long-term retention_days delete query
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ltm-pinned", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Capture the DeleteByQueryRequest to verify pinned exclusion
        BulkByScrollResponse ltmDeleteResponse = mock(BulkByScrollResponse.class);
        when(ltmDeleteResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            String queryString = dbq.getSearchRequest().source().query().toString();
            assertTrue("Long-term delete query should exclude pinned=true", queryString.contains("pinned"));

            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ltmDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testNullLtmIndex() {
        // Container without LLM/strategies -> getIndexName(LONG_TERM) returns null -> LTM phase skipped
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"long-term\":{\"retention_days\":30}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-no-llm", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // No delete-by-query or bulk delete should fire (LTM phase skipped due to null index)
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testLongTermBothRetentionDaysAndMaxCount() {
        // Both retention_days AND max_count set -> verify both operations fire sequentially
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"retention_days\":30,\"max_count\":100}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ltm-both", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Pinned count check (fire-and-forget): return 0 pinned docs
                    SearchResponse pinnedResp = mock(SearchResponse.class);
                    SearchHits pinnedHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(pinnedResp.getHits()).thenReturn(pinnedHits);
                    listener.onResponse(pinnedResp);
                } else if (searchNum == 1) {
                    // Count query for max_count: return 120
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(120, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    // Search for oldest 20 IDs
                    SearchHit[] hits = new SearchHit[20];
                    for (int i = 0; i < 20; i++) {
                        hits[i] = new SearchHit(i, "ltm-excess-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "ltm-excess-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits searchHits = new SearchHits(hits, new TotalHits(20, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse searchResp = mock(SearchResponse.class);
                    when(searchResp.getHits()).thenReturn(searchHits);
                    listener.onResponse(searchResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query for retention_days
        BulkByScrollResponse ltmDeleteResponse = mock(BulkByScrollResponse.class);
        when(ltmDeleteResponse.getDeleted()).thenReturn(3L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ltmDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        // Mock bulk delete for max_count
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        // Both operations should have fired
        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    // --- Pinned Exceeds max_count Warning Tests ---

    @Test
    public void testPinnedExceedsMaxCountWarningFires() {
        // Container with long-term max_count=10, pinned count returns 15 -> verify warning fires (no exception)
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"long-term\":{\"max_count\":10}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-pin-warn", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Pinned count check: return 15 pinned docs (exceeds max_count=10)
                    SearchResponse pinnedResp = mock(SearchResponse.class);
                    SearchHits pinnedHits = new SearchHits(new SearchHit[0], new TotalHits(15, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(pinnedResp.getHits()).thenReturn(pinnedHits);
                    listener.onResponse(pinnedResp);
                } else if (searchNum == 1) {
                    // Count query for max_count: return 5 non-pinned (under limit, no eviction)
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // Pinned count check and normal count query both fired (at least 2 non-container searches)
        assertTrue("Pinned count check and count query should both fire", nonContainerSearchCount.get() >= 2);
        // No bulk delete since non-pinned count (5) is under max_count (10)
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    // --- Phase 5: History Retention Tests ---

    @Test
    public void testHistoryMaxCount() {
        // Container with history policy max_count=500, 600 docs -> verify search sorted by created_time ASC
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"history\":{\"max_count\":500}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-history-count", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Pinned count check (fire-and-forget): return 0 pinned docs
                    SearchResponse pinnedResp = mock(SearchResponse.class);
                    SearchHits pinnedHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(pinnedResp.getHits()).thenReturn(pinnedHits);
                    listener.onResponse(pinnedResp);
                } else if (searchNum == 1) {
                    // Count query: return totalHits=600
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(600, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    // Search for oldest 100 IDs — verify sort field is created_time
                    String sourceStr = request.source().toString();
                    assertTrue("History search should sort by created_time", sourceStr.contains("created_time"));

                    SearchHit[] hits = new SearchHit[100];
                    for (int i = 0; i < 100; i++) {
                        hits[i] = new SearchHit(i, "history-doc-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "history-doc-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits searchHits = new SearchHits(hits, new TotalHits(100, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse searchResp = mock(SearchResponse.class);
                    when(searchResp.getHits()).thenReturn(searchHits);
                    listener.onResponse(searchResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testHistoryUsesCreatedTime() {
        // Verify the sort field in history search is created_time, not last_updated_time
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{\"history\":{\"max_count\":10}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-history-time", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger nonContainerSearchCount = new AtomicInteger(0);
        AtomicInteger verifiedCreatedTime = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                int searchNum = nonContainerSearchCount.getAndIncrement();
                if (searchNum == 0) {
                    // Pinned count check (fire-and-forget): return 0 pinned docs
                    SearchResponse pinnedResp = mock(SearchResponse.class);
                    SearchHits pinnedHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(pinnedResp.getHits()).thenReturn(pinnedHits);
                    listener.onResponse(pinnedResp);
                } else if (searchNum == 1) {
                    // Count query: return 15 docs (exceeds max_count=10)
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(15, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    // Verify sort uses created_time, NOT last_updated_time
                    String sourceStr = request.source().toString();
                    assertTrue("History must use created_time for sorting", sourceStr.contains("created_time"));
                    assertTrue("History must NOT use last_updated_time", !sourceStr.contains("last_updated_time"));
                    verifiedCreatedTime.incrementAndGet();

                    SearchHit[] hits = new SearchHit[5];
                    for (int i = 0; i < 5; i++) {
                        hits[i] = new SearchHit(i, "hist-" + i, null, null);
                        hits[i]
                            .sortValues(
                                new Object[] { (long) i, "hist-" + i },
                                new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW }
                            );
                    }
                    SearchHits searchHits = new SearchHits(hits, new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    SearchResponse searchResp = mock(SearchResponse.class);
                    when(searchResp.getHits()).thenReturn(searchHits);
                    listener.onResponse(searchResp);
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock bulk delete
        BulkResponse bulkResponse = mock(BulkResponse.class);
        lenient().when(bulkResponse.hasFailures()).thenReturn(false);
        lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);

        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Should have verified created_time sort field", verifiedCreatedTime.get() > 0);
    }

    // --- Phase 6: Working Memory TTL Tests ---

    @Test
    public void testWorkingMemoryTTLDisableSessionTrue() {
        // Container with disableSession=true -> verify _delete_by_query on working index with created_time range
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ttl-enabled", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query for working memory TTL
        BulkByScrollResponse ttlDeleteResponse = mock(BulkByScrollResponse.class);
        when(ttlDeleteResponse.getDeleted()).thenReturn(8L);

        doAnswer(invocation -> {
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            String queryString = dbq.getSearchRequest().source().query().toString();
            assertTrue("Working memory TTL should filter by memory_container_id", queryString.contains("memory_container_id"));
            assertTrue("Working memory TTL should use created_time", queryString.contains("created_time"));
            // Working memory does NOT support pinning, so no pinned filter
            assertTrue("Working memory TTL should NOT exclude pinned (no pinning support)", !queryString.contains("pinned"));

            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ttlDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testWorkingMemoryTTLDisableSessionFalse() {
        // Container with disableSession=false -> Phase 6 skipped entirely
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":false,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ttl-skipped", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // Session search returns no expired sessions
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // No delete-by-query should be called (session expired=0, and working memory TTL is skipped)
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testWorkingMemoryTTLDisabledByDefault() {
        // working_memory_ttl_days=-1 (the default, off): even a disable_session=true container must NOT have its
        // working memory aged out, so session-less working memory is kept indefinitely.
        Settings ttlOffSettings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_enabled", true)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.default_session_retention_days", -1)
            .put("plugins.ml_commons.memory.default_session_max_count", -1)
            .put("plugins.ml_commons.memory.default_long_term_max_count", -1)
            .put("plugins.ml_commons.memory.default_history_max_count", -1)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .put("plugins.ml_commons.memory.working_memory_ttl_days", -1)
            .build();
        when(clusterService.getSettings()).thenReturn(ttlOffSettings);
        java.util.Set<Setting<?>> ttlOffSet = new java.util.HashSet<>(
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
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(ttlOffSettings, ttlOffSet));

        // Container with disable_session=true AND a retention_policy, so Pass 1 runs the retention pipeline and
        // reaches the working-memory TTL stage. That stage must still be skipped because the TTL is off (-1).
        // (sessions retention_policy has no effect when disable_session=true; it just drives the pipeline to run.)
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-ttl-off", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        // TTL off -> no working-memory delete_by_query should ever run.
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    // --- All phases chain test ---

    @Test
    public void testAllPhasesChainCorrectly() {
        // Container with session, long-term, and history policies -> verify all three phases run
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"llm_id\":\"test-llm\","
            + "\"strategies\":[{\"type\":\"semantic\"}],"
            + "\"retention_policy\":{"
            + "\"sessions\":{\"retention_days\":7},"
            + "\"long-term\":{\"retention_days\":30},"
            + "\"history\":{\"max_count\":500}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-all-phases", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger deleteByQueryCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                // For count queries return 0 (under threshold), for other searches return empty
                SearchResponse countResp = mock(SearchResponse.class);
                SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
                when(countResp.getHits()).thenReturn(countHits);
                listener.onResponse(countResp);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Mock delete_by_query — should be called for session phase and long-term retention_days
        BulkByScrollResponse dbqResponse = mock(BulkByScrollResponse.class);
        when(dbqResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            deleteByQueryCount.incrementAndGet();
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(dbqResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        // Long-term retention_days should trigger a delete_by_query
        assertTrue("At least one delete_by_query should have been called for long-term retention_days", deleteByQueryCount.get() >= 1);
    }

    // --- Phase 7: Orphan Sweep Tests ---

    @Test
    public void testOrphanDetectedAndDeleted() {
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-orphan", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger orphanPhaseSearchCount = new AtomicInteger(0);
        boolean[] inOrphanPhase = { false };

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0) {
                    listener.onResponse(containerSearchResponse);
                } else if (count == 1) {
                    inOrphanPhase[0] = true;
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                String targetIndex = request.indices()[0];

                if (!inOrphanPhase[0]) {
                    if (request.source() != null && request.source().size() == 0) {
                        SearchResponse countResp = mock(SearchResponse.class);
                        SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                        when(countResp.getHits()).thenReturn(countHits);
                        listener.onResponse(countResp);
                    } else {
                        listener.onResponse(emptySearchResponse());
                    }
                } else {
                    orphanPhaseSearchCount.incrementAndGet();
                    if (targetIndex.contains("working")) {
                        SearchHit hit1 = new SearchHit(0, "working-1", null, null);
                        hit1.sourceRef(new BytesArray("{\"namespace\":{\"session_id\":\"session-exists\"}}"));
                        hit1.sortValues(new Object[] { "working-1" }, new DocValueFormat[] { DocValueFormat.RAW });

                        SearchHit hit2 = new SearchHit(1, "working-2", null, null);
                        hit2.sourceRef(new BytesArray("{\"namespace\":{\"session_id\":\"session-gone\"}}"));
                        hit2.sortValues(new Object[] { "working-2" }, new DocValueFormat[] { DocValueFormat.RAW });

                        SearchHits workingHits = new SearchHits(
                            new SearchHit[] { hit1, hit2 },
                            new TotalHits(2, TotalHits.Relation.EQUAL_TO),
                            Float.NaN
                        );
                        SearchResponse workingResponse = mock(SearchResponse.class);
                        when(workingResponse.getHits()).thenReturn(workingHits);
                        listener.onResponse(workingResponse);
                    } else if (targetIndex.contains("sessions")) {
                        SearchHit existsHit = new SearchHit(0, "session-exists", null, null);
                        SearchHits sessionHits = new SearchHits(
                            new SearchHit[] { existsHit },
                            new TotalHits(1, TotalHits.Relation.EQUAL_TO),
                            Float.NaN
                        );
                        SearchResponse sessionResponse = mock(SearchResponse.class);
                        when(sessionResponse.getHits()).thenReturn(sessionHits);
                        listener.onResponse(sessionResponse);
                    } else {
                        listener.onResponse(emptySearchResponse());
                    }
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse orphanDeleteResponse = mock(BulkByScrollResponse.class);
        when(orphanDeleteResponse.getDeleted()).thenReturn(3L);

        AtomicInteger dbqCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            dbqCount.incrementAndGet();
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(orphanDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Orphan sweep should have searched working memory", orphanPhaseSearchCount.get() >= 1);
        assertTrue("Delete-by-query should fire for orphans and null sessions", dbqCount.get() >= 2);
    }

    @Test
    public void testOrphanSweepFirstObservationStampsBaselineAndDefersDeletion() {
        // Container has NO orphan_sweep_baseline_time: this is the first time the sweep observes it. The sweep
        // must stamp a baseline (client.update) and defer ALL orphan deletion for one orphan_ttl_days window,
        // so pre-existing session-less working memory is not deleted on the first run after retention is enabled.
        String sourceJson = "{\""
            + ORPHAN_SWEEP_BASELINE_TIME_FIELD
            + "\":null,"
            + "\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-first-obs", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0 || count == 1) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                if (request.source() != null && request.source().size() == 0) {
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        AtomicInteger baselineUpdateCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            org.opensearch.action.update.UpdateRequest updateRequest = invocation.getArgument(0);
            if (ML_MEMORY_CONTAINER_INDEX.equals(updateRequest.index()) && "container-first-obs".equals(updateRequest.id())) {
                baselineUpdateCount.incrementAndGet();
            }
            ActionListener<org.opensearch.action.update.UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(org.opensearch.action.update.UpdateResponse.class));
            return null;
        }).when(client).update(any(org.opensearch.action.update.UpdateRequest.class), isA(ActionListener.class));

        AtomicInteger orphanDbqCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            orphanDbqCount.incrementAndGet();
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(mock(BulkByScrollResponse.class));
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("First observation should stamp an orphan-sweep baseline", baselineUpdateCount.get() >= 1);
        assertEquals("No orphan deletion should occur on the first observation", 0, orphanDbqCount.get());
    }

    @Test
    public void testOrphanEnumerationUsesCompositeAggregation() {
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-agg", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger compositeAggSearchCount = new AtomicInteger(0);
        boolean[] inOrphanPhase = { false };

        CompositeAggregation.Bucket bucket = mock(CompositeAggregation.Bucket.class);
        when(bucket.getKey()).thenReturn(Map.of("session_id", "session-gone"));
        CompositeAggregation composite = mock(CompositeAggregation.class);
        when(composite.getName()).thenReturn("session_ids");
        doReturn(java.util.List.of(bucket)).when(composite).getBuckets();
        when(composite.afterKey()).thenReturn(null);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0) {
                    listener.onResponse(containerSearchResponse);
                } else if (count == 1) {
                    inOrphanPhase[0] = true;
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                String targetIndex = request.indices()[0];

                if (!inOrphanPhase[0]) {
                    if (request.source() != null && request.source().size() == 0) {
                        SearchResponse countResp = mock(SearchResponse.class);
                        SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                        when(countResp.getHits()).thenReturn(countHits);
                        listener.onResponse(countResp);
                    } else {
                        listener.onResponse(emptySearchResponse());
                    }
                } else if (targetIndex.contains("working") && request.source() != null && request.source().aggregations() != null) {
                    compositeAggSearchCount.incrementAndGet();
                    SearchResponse aggResponse = mock(SearchResponse.class);
                    when(aggResponse.getAggregations()).thenReturn(new Aggregations(java.util.List.of(composite)));
                    listener.onResponse(aggResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse orphanDeleteResponse = mock(BulkByScrollResponse.class);
        when(orphanDeleteResponse.getDeleted()).thenReturn(1L);

        AtomicInteger dbqCount = new AtomicInteger(0);
        java.util.List<String> dbqQueries = new java.util.ArrayList<>();

        doAnswer(invocation -> {
            dbqCount.incrementAndGet();
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            dbqQueries.add(dbq.getSearchRequest().source().query().toString());
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(orphanDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Orphan enumeration should use a composite aggregation search", compositeAggSearchCount.get() >= 1);
        assertTrue("Orphan delete and empty-namespace DBQ should fire", dbqCount.get() >= 2);
        assertTrue(
            "Orphan DBQ should match the aggregated session id on the top-level session_id field",
            dbqQueries.stream().anyMatch(q -> q.contains("session-gone") && q.contains("\"session_id\""))
        );
    }

    @Test
    public void testOrphanEnumerationStartsAtRandomKeyAndWrapsAround() {
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-random-start", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        boolean[] inOrphanPhase = { false };
        java.util.List<String> compositeAggSources = new java.util.ArrayList<>();

        CompositeAggregation.Bucket bucket = mock(CompositeAggregation.Bucket.class);
        when(bucket.getKey()).thenReturn(Map.of("session_id", "session-gone"));
        CompositeAggregation composite = mock(CompositeAggregation.class);
        when(composite.getName()).thenReturn("session_ids");
        doReturn(java.util.List.of(bucket)).when(composite).getBuckets();
        when(composite.afterKey()).thenReturn(null);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0) {
                    listener.onResponse(containerSearchResponse);
                } else if (count == 1) {
                    inOrphanPhase[0] = true;
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                String targetIndex = request.indices()[0];

                if (!inOrphanPhase[0]) {
                    if (request.source() != null && request.source().size() == 0) {
                        SearchResponse countResp = mock(SearchResponse.class);
                        SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                        when(countResp.getHits()).thenReturn(countHits);
                        listener.onResponse(countResp);
                    } else {
                        listener.onResponse(emptySearchResponse());
                    }
                } else if (targetIndex.contains("working") && request.source() != null && request.source().aggregations() != null) {
                    compositeAggSources.add(request.source().toString());
                    SearchResponse aggResponse = mock(SearchResponse.class);
                    when(aggResponse.getAggregations()).thenReturn(new Aggregations(java.util.List.of(composite)));
                    listener.onResponse(aggResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse orphanDeleteResponse = mock(BulkByScrollResponse.class);
        when(orphanDeleteResponse.getDeleted()).thenReturn(1L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(orphanDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Orphan enumeration should issue at least the initial composite search", compositeAggSources.size() >= 1);
        assertTrue(
            "First composite search must start from a randomized after key so cap truncation covers a different slice each run",
            compositeAggSources.get(0).contains("\"after\"")
        );
        assertTrue(
            "Enumeration must wrap around to the start of the keyspace after exhausting the first slice",
            compositeAggSources.size() >= 2 && !compositeAggSources.get(compositeAggSources.size() - 1).contains("\"after\"")
        );
    }

    @Test
    public void testNullSessionIdDeletedAfterGracePeriod() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .build();
        when(clusterService.getSettings()).thenReturn(settings);

        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-null-session", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0 || count == 1) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                if (request.source() != null && request.source().size() == 0) {
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse nullDeleteResponse = mock(BulkByScrollResponse.class);
        when(nullDeleteResponse.getDeleted()).thenReturn(5L);

        AtomicInteger deleteByQueryCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            deleteByQueryCount.incrementAndGet();
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            String queryString = dbq.getSearchRequest().source().query().toString();
            assertTrue("Null session delete should use created_time range", queryString.contains("created_time"));

            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(nullDeleteResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Null session delete_by_query should fire", deleteByQueryCount.get() >= 1);
    }

    @Test
    public void testNullSessionIdWithinGracePeriodNotDeleted() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .build();
        when(clusterService.getSettings()).thenReturn(settings);

        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-grace", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0 || count == 1) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                if (request.source() != null && request.source().size() == 0) {
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse graceResponse = mock(BulkByScrollResponse.class);
        when(graceResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            String queryString = dbq.getSearchRequest().source().query().toString();
            assertTrue("Null session delete should use created_time range for grace period", queryString.contains("created_time"));
            assertTrue("Should filter docs without session_id", queryString.contains("session_id"));

            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(graceResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, atLeastOnce()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testNoOrphansFound() {
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-no-orphans", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger deleteByQueryCount = new AtomicInteger(0);
        boolean[] inOrphanPhase = { false };

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0) {
                    listener.onResponse(containerSearchResponse);
                } else if (count == 1) {
                    inOrphanPhase[0] = true;
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                String targetIndex = request.indices()[0];

                if (!inOrphanPhase[0]) {
                    if (request.source() != null && request.source().size() == 0) {
                        SearchResponse countResp = mock(SearchResponse.class);
                        SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                        when(countResp.getHits()).thenReturn(countHits);
                        listener.onResponse(countResp);
                    } else {
                        listener.onResponse(emptySearchResponse());
                    }
                } else {
                    if (targetIndex.contains("working")) {
                        SearchHit hit1 = new SearchHit(0, "w-1", null, null);
                        hit1.sourceRef(new BytesArray("{\"namespace\":{\"session_id\":\"s-1\"}}"));
                        hit1.sortValues(new Object[] { "w-1" }, new DocValueFormat[] { DocValueFormat.RAW });

                        SearchHit hit2 = new SearchHit(1, "w-2", null, null);
                        hit2.sourceRef(new BytesArray("{\"namespace\":{\"session_id\":\"s-2\"}}"));
                        hit2.sortValues(new Object[] { "w-2" }, new DocValueFormat[] { DocValueFormat.RAW });

                        SearchHits workingHits = new SearchHits(
                            new SearchHit[] { hit1, hit2 },
                            new TotalHits(2, TotalHits.Relation.EQUAL_TO),
                            Float.NaN
                        );
                        SearchResponse workingResponse = mock(SearchResponse.class);
                        when(workingResponse.getHits()).thenReturn(workingHits);
                        listener.onResponse(workingResponse);
                    } else if (targetIndex.contains("sessions")) {
                        SearchHit exist1 = new SearchHit(0, "s-1", null, null);
                        SearchHit exist2 = new SearchHit(1, "s-2", null, null);
                        SearchHits sessionHits = new SearchHits(
                            new SearchHit[] { exist1, exist2 },
                            new TotalHits(2, TotalHits.Relation.EQUAL_TO),
                            Float.NaN
                        );
                        SearchResponse sessionResponse = mock(SearchResponse.class);
                        when(sessionResponse.getHits()).thenReturn(sessionHits);
                        listener.onResponse(sessionResponse);
                    } else {
                        listener.onResponse(emptySearchResponse());
                    }
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse nullResponse = mock(BulkByScrollResponse.class);
        when(nullResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            deleteByQueryCount.incrementAndGet();
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(nullResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Only null session cleanup DBQ should fire (no orphans)", deleteByQueryCount.get() == 1);
    }

    @Test
    public void testOrphanSweepSkippedWhenSessionsIndexDoesNotExist() {
        // Sessions index does not exist in cluster state: sweep must be skipped entirely,
        // otherwise all working memory would be classified as orphaned and deleted.
        lenient().when(clusterService.state().metadata().hasIndex(anyString())).thenAnswer(invocation -> {
            String indexName = invocation.getArgument(0);
            return !indexName.contains("sessions");
        });

        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-idx-missing", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger workingMemorySearchCount = new AtomicInteger(0);
        boolean[] inOrphanPhase = { false };

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0) {
                    listener.onResponse(containerSearchResponse);
                } else if (count == 1) {
                    inOrphanPhase[0] = true;
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                if (inOrphanPhase[0] && request.indices()[0].contains("working")) {
                    workingMemorySearchCount.incrementAndGet();
                }
                if (request.source() != null && request.source().size() == 0) {
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        AtomicInteger orphanDbqCount = new AtomicInteger(0);

        BulkByScrollResponse dbqResponse = mock(BulkByScrollResponse.class);
        lenient().when(dbqResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            if (inOrphanPhase[0]) {
                orphanDbqCount.incrementAndGet();
            }
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(dbqResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Orphan sweep must not enumerate working memory when sessions index is missing", workingMemorySearchCount.get() == 0);
        assertTrue("Orphan sweep must not delete anything when sessions index is missing", orphanDbqCount.get() == 0);
    }

    @Test
    public void testOrphanSweepSkippedWhenWorkingMemoryIndexDoesNotExist() {
        // Working memory index does not exist: nothing to sweep, skip.
        lenient().when(clusterService.state().metadata().hasIndex(anyString())).thenAnswer(invocation -> {
            String indexName = invocation.getArgument(0);
            return !indexName.contains("working");
        });

        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-wm-missing", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger workingMemorySearchCount = new AtomicInteger(0);
        boolean[] inOrphanPhase = { false };

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0) {
                    listener.onResponse(containerSearchResponse);
                } else if (count == 1) {
                    inOrphanPhase[0] = true;
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                if (inOrphanPhase[0] && request.indices()[0].contains("working")) {
                    workingMemorySearchCount.incrementAndGet();
                }
                if (request.source() != null && request.source().size() == 0) {
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse dbqResponse = mock(BulkByScrollResponse.class);
        lenient().when(dbqResponse.getDeleted()).thenReturn(0L);

        AtomicInteger orphanDbqCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            if (inOrphanPhase[0]) {
                orphanDbqCount.incrementAndGet();
            }
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(dbqResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue(
            "Orphan sweep must not enumerate working memory when working memory index is missing",
            workingMemorySearchCount.get() == 0
        );
        assertTrue("Orphan sweep must not delete anything when working memory index is missing", orphanDbqCount.get() == 0);
    }

    @Test
    public void testSessionsIndexMissing() {
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-no-sessions-idx", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        AtomicInteger orphanDbqCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse ttlResponse = mock(BulkByScrollResponse.class);
        when(ttlResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            orphanDbqCount.incrementAndGet();
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(ttlResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Only Phase 6 TTL DBQ should fire, not orphan sweep", orphanDbqCount.get() == 1);
    }

    @Test
    public void testDisableSessionTrueContainerSkippedInOrphanSweep() {
        String sourceJsonSessionEnabled = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"retention_policy\":{\"sessions\":{\"max_count\":1000}}}}";

        String sourceJsonSessionDisabled = "{\"configuration\":{\"index_prefix\":\"other-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";

        SearchHit hit1 = new SearchHit(0, "container-with-session", null, null);
        hit1.sourceRef(new BytesArray(sourceJsonSessionEnabled));
        hit1.sortValues(new Object[] { "container-with-session" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchHit hit2 = new SearchHit(1, "container-no-session", null, null);
        hit2.sourceRef(new BytesArray(sourceJsonSessionDisabled));
        hit2.sortValues(new Object[] { "container-no-session" }, new DocValueFormat[] { DocValueFormat.RAW });

        SearchResponse retentionContainerResponse = mock(SearchResponse.class);
        SearchHits retentionContainerHits = new SearchHits(
            new SearchHit[] { hit1, hit2 },
            new TotalHits(2, TotalHits.Relation.EQUAL_TO),
            Float.NaN
        );
        when(retentionContainerResponse.getHits()).thenReturn(retentionContainerHits);

        SearchResponse orphanContainerResponse = createContainerSearchResponseFromJson("container-with-session", sourceJsonSessionEnabled);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);

            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                int count = containerSearchCount.getAndIncrement();
                if (count == 0) {
                    listener.onResponse(retentionContainerResponse);
                } else if (count == 1) {
                    listener.onResponse(orphanContainerResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                if (request.source() != null && request.source().size() == 0) {
                    SearchResponse countResp = mock(SearchResponse.class);
                    SearchHits countHits = new SearchHits(new SearchHit[0], new TotalHits(5, TotalHits.Relation.EQUAL_TO), Float.NaN);
                    when(countResp.getHits()).thenReturn(countHits);
                    listener.onResponse(countResp);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse dbqResponse = mock(BulkByScrollResponse.class);
        when(dbqResponse.getDeleted()).thenReturn(0L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(dbqResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Both retention and orphan sweep container searches should fire", containerSearchCount.get() >= 2);
    }

    @Test
    public void testApplyDefaultRetentionPolicy_IssuesUpdateWhenDefaultsConfigured() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_enabled", true)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.default_session_retention_days", 30)
            .put("plugins.ml_commons.memory.default_session_max_count", 100)
            .put("plugins.ml_commons.memory.default_long_term_max_count", 500)
            .put("plugins.ml_commons.memory.default_history_max_count", -1)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .put("plugins.ml_commons.memory.working_memory_ttl_days", 30)
            .build();
        when(clusterService.getSettings()).thenReturn(settings);
        java.util.Set<org.opensearch.common.settings.Setting<?>> s = new java.util.HashSet<>(
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
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, s));

        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\",\"use_system_index\":true}}";
        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-no-policy", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicBoolean updateCalled = new java.util.concurrent.atomic.AtomicBoolean(false);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            updateCalled.set(true);
            org.opensearch.action.update.UpdateRequest req = invocation.getArgument(0);
            assertTrue("Update should use a script", req.script() != null);
            assertTrue("Script should contain noop logic", req.script().getIdOrCode().contains("noop"));
            ActionListener<org.opensearch.action.update.UpdateResponse> listener = invocation.getArgument(1);
            org.opensearch.action.update.UpdateResponse resp = mock(org.opensearch.action.update.UpdateResponse.class);
            when(resp.getResult()).thenReturn(org.opensearch.action.DocWriteResponse.Result.UPDATED);
            listener.onResponse(resp);
            return null;
        }).when(client).update(any(org.opensearch.action.update.UpdateRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("client.update should be called to backfill default policy", updateCalled.get());
    }

    @Test
    public void testApplyDefaultRetentionPolicy_NoopSkipsContainer() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_enabled", true)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.default_session_retention_days", 30)
            .put("plugins.ml_commons.memory.default_session_max_count", -1)
            .put("plugins.ml_commons.memory.default_long_term_max_count", -1)
            .put("plugins.ml_commons.memory.default_history_max_count", -1)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .put("plugins.ml_commons.memory.working_memory_ttl_days", 30)
            .build();
        when(clusterService.getSettings()).thenReturn(settings);
        java.util.Set<org.opensearch.common.settings.Setting<?>> s = new java.util.HashSet<>(
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
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, s));

        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\",\"use_system_index\":true}}";
        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-no-policy-noop", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.update.UpdateResponse> listener = invocation.getArgument(1);
            org.opensearch.action.update.UpdateResponse resp = mock(org.opensearch.action.update.UpdateResponse.class);
            when(resp.getResult()).thenReturn(org.opensearch.action.DocWriteResponse.Result.NOOP);
            listener.onResponse(resp);
            return null;
        }).when(client).update(any(org.opensearch.action.update.UpdateRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testApplyDefaultRetentionPolicy_AllDefaultsMinusOne_NoUpdate() {
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\",\"use_system_index\":true}}";
        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-all-minus-one", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, never()).update(any(org.opensearch.action.update.UpdateRequest.class), isA(ActionListener.class));
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testEpochSecondNormalization_NewerTimestampNotDeleted() {
        SearchResponse containerSearchResponse = createContainerSearchResponse(
            "container-epoch",
            "test-prefix",
            true,
            createRetentionPolicyMap("sessions", 7, null)
        );

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        java.util.List<String> deletedIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        long nowMillis = System.currentTimeMillis();
        long cutoffMillis = nowMillis - java.util.concurrent.TimeUnit.DAYS.toMillis(7);
        long epochSecNewSession = (nowMillis - java.util.concurrent.TimeUnit.DAYS.toMillis(3)) / 1000;
        long epochMillisOldSession = cutoffMillis - 10_000;

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                SearchHit hitNew = createHitWithSource("session-epoch-new", Map.of("last_updated_time", epochSecNewSession));
                SearchHit hitOld = createHitWithSource("session-epoch-old", Map.of("last_updated_time", epochMillisOldSession));
                SearchHit hitMissing = createHitWithSource("session-missing-ts", Map.of("other_field", "value"));
                SearchHits sessionHits = new SearchHits(
                    new SearchHit[] { hitNew, hitOld, hitMissing },
                    new TotalHits(3, TotalHits.Relation.EQUAL_TO),
                    Float.NaN
                );
                SearchResponse sessionResponse = mock(SearchResponse.class);
                when(sessionResponse.getHits()).thenReturn(sessionHits);
                listener.onResponse(sessionResponse);
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
        when(cascadeResponse.getDeleted()).thenReturn(1L);

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(cascadeResponse);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            BulkRequest bulkReq = invocation.getArgument(0);
            bulkReq.requests().forEach(r -> deletedIds.add(((org.opensearch.action.delete.DeleteRequest) r).id()));
            BulkResponse bulkResponse = mock(BulkResponse.class);
            lenient().when(bulkResponse.hasFailures()).thenReturn(false);
            lenient().when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

        processor.run();

        assertTrue("Epoch-second session newer than cutoff should NOT be deleted", !deletedIds.contains("session-epoch-new"));
        assertTrue("Epoch-millis session older than cutoff SHOULD be deleted", deletedIds.contains("session-epoch-old"));
        assertTrue("Session with missing timestamp should NOT be deleted", !deletedIds.contains("session-missing-ts"));
    }

    @Test
    public void testBuildEpochAwareCutoffQuery_Structure() {
        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\","
            + "\"use_system_index\":true,"
            + "\"disable_session\":true,"
            + "\"retention_policy\":{\"sessions\":{\"retention_days\":7}}}}";
        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-dbq-cutoff", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> capturedQuery = new java.util.concurrent.atomic.AtomicReference<>();

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            DeleteByQueryRequest dbq = invocation.getArgument(1);
            capturedQuery.set(dbq.getSearchRequest().source().query().toString());
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse resp = mock(BulkByScrollResponse.class);
            when(resp.getDeleted()).thenReturn(0L);
            listener.onResponse(resp);
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

        processor.run();

        String query = capturedQuery.get();
        assertTrue("Query must contain gte threshold clause", query != null && query.contains("10000000000"));
        assertTrue("Query must contain minimum_should_match=1", query.contains("minimum_should_match") && query.contains("1"));
        assertTrue("Query must have two should clauses for epoch-aware cutoff", query.contains("should"));
    }

    @Test
    public void testDebugLogsSessionDeletionIds() {
        org.apache.logging.log4j.core.LoggerContext context =
            (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        org.apache.logging.log4j.core.config.LoggerConfig loggerConfig = context
            .getConfiguration()
            .getLoggerConfig(MemoryRetentionJobProcessor.class.getName());
        org.apache.logging.log4j.Level originalLevel = loggerConfig.getLevel();
        loggerConfig.setLevel(org.apache.logging.log4j.Level.DEBUG);
        context.updateLoggers();

        java.util.List<String> capturedMessages = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        org.apache.logging.log4j.core.appender.AbstractAppender appender = new org.apache.logging.log4j.core.appender.AbstractAppender(
            "test-debug-appender",
            null,
            org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(),
            false
        ) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent event) {
                capturedMessages.add(event.getLevel() + ": " + event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        loggerConfig.addAppender(appender, org.apache.logging.log4j.Level.DEBUG, null);
        context.updateLoggers();

        try {
            SearchResponse containerSearchResponse = createContainerSearchResponse(
                "container-debug",
                "test-prefix",
                true,
                createRetentionPolicyMap("sessions", 7, null)
            );

            AtomicInteger containerSearchCount = new AtomicInteger(0);

            doAnswer(invocation -> {
                SearchRequest request = invocation.getArgument(0);
                ActionListener<SearchResponse> listener = invocation.getArgument(1);
                if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                    if (containerSearchCount.getAndIncrement() == 0) {
                        listener.onResponse(containerSearchResponse);
                    } else {
                        listener.onResponse(emptySearchResponse());
                    }
                } else {
                    long oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                    SearchHit hit1 = createHitWithSource("debug-session-1", Map.of("last_updated_time", oldTimestamp));
                    SearchHit hit2 = createHitWithSource("debug-session-2", Map.of("last_updated_time", oldTimestamp));
                    SearchHits sessionHits = new SearchHits(
                        new SearchHit[] { hit1, hit2 },
                        new TotalHits(2, TotalHits.Relation.EQUAL_TO),
                        Float.NaN
                    );
                    SearchResponse sessionResponse = mock(SearchResponse.class);
                    when(sessionResponse.getHits()).thenReturn(sessionHits);
                    listener.onResponse(sessionResponse);
                }
                return null;
            }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

            BulkByScrollResponse cascadeResponse = mock(BulkByScrollResponse.class);
            when(cascadeResponse.getDeleted()).thenReturn(2L);
            doAnswer(invocation -> {
                ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
                listener.onResponse(cascadeResponse);
                return null;
            }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));

            BulkResponse bulkResponse = mock(BulkResponse.class);
            when(bulkResponse.hasFailures()).thenReturn(false);
            when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[0]);
            doAnswer(invocation -> {
                ActionListener<BulkResponse> listener = invocation.getArgument(1);
                listener.onResponse(bulkResponse);
                return null;
            }).when(client).bulk(any(BulkRequest.class), isA(ActionListener.class));

            processor.run();

            boolean foundDebugIds = capturedMessages
                .stream()
                .anyMatch(m -> m.startsWith("DEBUG:") && m.contains("deleting session ids=") && m.contains("debug-session-1"));
            assertTrue("DEBUG log should contain session IDs being deleted", foundDebugIds);

            boolean foundInfoAggregate = capturedMessages.stream().anyMatch(m -> m.startsWith("INFO:") && m.contains("sessions_expired=2"));
            assertTrue("INFO aggregate log should be unchanged", foundInfoAggregate);
        } finally {
            loggerConfig.removeAppender(appender.getName());
            appender.stop();
            loggerConfig.setLevel(originalLevel);
            context.updateLoggers();
        }
    }

    @Test
    public void testRetentionPolicyNullOptOut_SkipsEverything() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_enabled", true)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.default_session_retention_days", 30)
            .put("plugins.ml_commons.memory.default_session_max_count", 100)
            .put("plugins.ml_commons.memory.default_long_term_max_count", 500)
            .put("plugins.ml_commons.memory.default_history_max_count", 200)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .put("plugins.ml_commons.memory.working_memory_ttl_days", 30)
            .build();
        when(clusterService.getSettings()).thenReturn(settings);
        java.util.Set<org.opensearch.common.settings.Setting<?>> s = new java.util.HashSet<>(
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
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, s));

        String sourceJson = "{\"configuration\":{\"index_prefix\":\"test-prefix\",\"use_system_index\":true,\"retention_policy\":null}}";
        SearchResponse containerSearchResponse = createContainerSearchResponseFromJson("container-opt-out", sourceJson);

        AtomicInteger containerSearchCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            if (request.indices()[0].equals(ML_MEMORY_CONTAINER_INDEX)) {
                if (containerSearchCount.getAndIncrement() == 0) {
                    listener.onResponse(containerSearchResponse);
                } else {
                    listener.onResponse(emptySearchResponse());
                }
            } else {
                listener.onResponse(emptySearchResponse());
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        processor.run();

        verify(client, never()).update(any(org.opensearch.action.update.UpdateRequest.class), isA(ActionListener.class));
        verify(client, never()).execute(eq(DeleteByQueryAction.INSTANCE), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
    }

    // --- Helper methods ---

    private SearchResponse createContainerSearchResponseFromJson(String containerId, String sourceJson) {
        SearchResponse searchResponse = mock(SearchResponse.class);
        SearchHit hit = new SearchHit(0, containerId, null, null);
        // Inject an already-elapsed orphan-sweep baseline so the first-observation grace window has passed and
        // the orphan sweep proceeds (steady state after the container was first observed in a prior run). Tests
        // that specifically exercise the "first observation stamps a baseline and defers deletion" behavior build
        // their container source directly and omit this. Non-orphan retention paths ignore the field.
        if (!sourceJson.contains(ORPHAN_SWEEP_BASELINE_TIME_FIELD)) {
            long elapsedBaseline = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365);
            sourceJson = sourceJson.replaceFirst("\\{", "{\"" + ORPHAN_SWEEP_BASELINE_TIME_FIELD + "\":" + elapsedBaseline + ",");
        }
        hit.sourceRef(new BytesArray(sourceJson));
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }

    private SearchResponse createContainerSearchResponse(
        String containerId,
        String indexPrefix,
        boolean useSystemIndex,
        Map<String, Object> retentionPolicy
    ) {
        // Build the JSON source for the container hit
        StringBuilder retentionJson = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : retentionPolicy.entrySet()) {
            if (!first)
                retentionJson.append(",");
            first = false;
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) entry.getValue();
            retentionJson.append("\"").append(entry.getKey()).append("\":{");
            boolean ruleFirst = true;
            if (rule.containsKey("retention_days")) {
                retentionJson.append("\"retention_days\":").append(rule.get("retention_days"));
                ruleFirst = false;
            }
            if (rule.containsKey("max_count")) {
                if (!ruleFirst)
                    retentionJson.append(",");
                retentionJson.append("\"max_count\":").append(rule.get("max_count"));
            }
            retentionJson.append("}");
        }
        retentionJson.append("}");

        String sourceJson = "{\"configuration\":{\"index_prefix\":\""
            + indexPrefix
            + "\","
            + "\"use_system_index\":"
            + useSystemIndex
            + ","
            + "\"retention_policy\":"
            + retentionJson.toString()
            + "}}";

        return createContainerSearchResponseFromJson(containerId, sourceJson);
    }

    private Map<String, Object> createRetentionPolicyMap(String memoryType, Integer retentionDays, Integer maxCount) {
        Map<String, Object> retentionPolicy = new HashMap<>();
        Map<String, Object> rule = new HashMap<>();
        if (retentionDays != null) {
            rule.put("retention_days", retentionDays);
        }
        if (maxCount != null) {
            rule.put("max_count", maxCount);
        }
        retentionPolicy.put(memoryType, rule);
        return retentionPolicy;
    }

    private SearchResponse emptySearchResponse() {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(response.getHits()).thenReturn(hits);
        return response;
    }

    private SearchHit createHitWithSource(String id, Map<String, Object> sourceMap) {
        SearchHit hit = new SearchHit(id.hashCode(), id, null, null);
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            if (!first)
                json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                json.append("\"").append(val).append("\"");
            } else {
                json.append(val);
            }
            first = false;
        }
        json.append("}");
        hit.sourceRef(new BytesArray(json.toString()));
        return hit;
    }

    @Test
    public void testCollectOldestDocIdsCapsAtCountBasedCap() throws Exception {
        // Regression guard for the heap-exhaustion fix: collectOldestDocIds must not accumulate more than
        // COUNT_BASED_ID_CAP IDs in a single run, even when the caller requests far more (large backlog).
        Field capField = MemoryRetentionJobProcessor.class.getDeclaredField("COUNT_BASED_ID_CAP");
        capField.setAccessible(true);
        final int cap = (int) capField.get(null);

        final int pageSize = 100; // == CONTAINER_PAGE_SIZE
        final AtomicInteger nextId = new AtomicInteger(0);

        // Every search page returns a full page of unique, monotonically increasing hits, so the pager would
        // page forever (collecting all requested IDs) if the cap were not enforced.
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchHit[] hits = new SearchHit[pageSize];
            for (int i = 0; i < pageSize; i++) {
                int id = nextId.getAndIncrement();
                hits[i] = new SearchHit(id, "doc-" + id, null, null);
                hits[i]
                    .sortValues(new Object[] { (long) id, "doc-" + id }, new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW });
            }
            SearchHits searchHits = new SearchHits(hits, new TotalHits(pageSize, TotalHits.Relation.EQUAL_TO), Float.NaN);
            SearchResponse response = mock(SearchResponse.class);
            when(response.getHits()).thenReturn(searchHits);
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // Request eviction of far more docs than the cap allows in a single run.
        int requestedLimit = cap + 10_000;

        Method collect = MemoryRetentionJobProcessor.class
            .getDeclaredMethod("collectOldestDocIds", String.class, String.class, String.class, int.class, ActionListener.class);
        collect.setAccessible(true);

        AtomicReference<Set<String>> result = new AtomicReference<>();
        ActionListener<Set<String>> listener = ActionListener.wrap(result::set, e -> fail("collectOldestDocIds failed: " + e));

        collect.invoke(processor, "long-term-index", "container-huge", "created_time", requestedLimit, listener);

        assertNotNull("collectOldestDocIds should have completed synchronously", result.get());
        assertEquals("collectOldestDocIds must not collect more than the per-run cap", cap, result.get().size());
    }
}

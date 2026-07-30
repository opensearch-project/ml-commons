/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.memorycontainer.RetentionRule;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.transport.memorycontainer.MemoryRetentionDryRunResult;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Tests for the retention dry-run compute path on {@link MemoryRetentionJobProcessor}. Verifies the
 * reported would_delete counts and by_reason attribution match the real job's identify/count logic, and
 * that the dry-run performs ZERO deletions (no delete, bulk, delete-by-query, or update calls).
 *
 * Search responses are routed by target index and query shape so each pass's reuse of the real
 * identify/count methods is exercised independently.
 */
@RunWith(MockitoJUnitRunner.class)
public class MemoryRetentionDryRunTests {

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

    private static final String PREFIX = "test-memory-";

    @Before
    public void setUp() {
        MemoryRetentionJobProcessor.reset();
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
        applySettings(settings);

        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        lenient().when(clusterService.state()).thenReturn(clusterState);
        lenient().when(clusterState.metadata()).thenReturn(metadata);
        lenient().when(metadata.hasIndex(anyString())).thenReturn(true);

        threadContext = new ThreadContext(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        processor = MemoryRetentionJobProcessor.getInstance(clusterService, client, threadPool);
    }

    private void applySettings(Settings settings) {
        when(clusterService.getSettings()).thenReturn(settings);
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
    }

    // ---- config builders ----

    private MemoryConfiguration sessionConfig(Integer retentionDays, Integer maxCount) {
        java.util.Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.SESSIONS, new RetentionRule(retentionDays, maxCount));
        return MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .useSystemIndex(false)
            .disableSession(false)
            .retentionPolicy(policy)
            .build();
    }

    /** Config with LLM + strategies so long-term/history indices resolve. */
    private MemoryConfiguration fullConfig(java.util.Map<MemoryType, RetentionRule> policy, boolean disableSession) {
        MemoryStrategy strategy = MemoryStrategy
            .builder()
            .id("s1")
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(List.of("user_id"))
            .enabled(true)
            .build();
        return MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .useSystemIndex(false)
            .disableSession(disableSession)
            .llmId("llm-1")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("emb-1")
            .dimension(768)
            .strategies(List.of(strategy))
            .retentionPolicy(policy)
            .build();
    }

    // ---- response helpers ----

    private SearchResponse countResp(long total) {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(total, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(response.getHits()).thenReturn(hits);
        return response;
    }

    private SearchResponse hitsResp(SearchHit[] hits) {
        SearchResponse response = mock(SearchResponse.class);
        SearchHits searchHits = new SearchHits(hits, new TotalHits(hits.length, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(response.getHits()).thenReturn(searchHits);
        return response;
    }

    private SearchHit tsHit(String id, long lastUpdated) {
        SearchHit hit = new SearchHit(id.hashCode(), id, null, null);
        hit.sourceRef(new BytesArray("{\"last_updated_time\":" + lastUpdated + "}"));
        return hit;
    }

    private SearchHit idHit(String id, long timeSort) {
        SearchHit hit = new SearchHit(id.hashCode(), id, null, null);
        hit.sortValues(new Object[] { timeSort, id }, new DocValueFormat[] { DocValueFormat.RAW, DocValueFormat.RAW });
        return hit;
    }

    private boolean isCount(SearchRequest r) {
        return r.source().size() == 0;
    }

    private boolean isFetchSourceDisabled(SearchRequest r) {
        return r.source().fetchSource() != null && !r.source().fetchSource().fetchSource();
    }

    private String q(SearchRequest r) {
        return r.source().query() == null ? "" : r.source().query().toString();
    }

    private void assertNoDeletes() {
        verify(client, never()).execute(isA(DeleteByQueryAction.class), any(DeleteByQueryRequest.class), isA(ActionListener.class));
        verify(client, never()).bulk(any(BulkRequest.class), isA(ActionListener.class));
        verify(client, never()).delete(any(DeleteRequest.class), isA(ActionListener.class));
        verify(client, never()).update(any(UpdateRequest.class), isA(ActionListener.class));
    }

    private MemoryRetentionDryRunResult run(MemoryConfiguration config, Long baseline) {
        AtomicReference<MemoryRetentionDryRunResult> ref = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        processor.dryRunContainer(config, "container-1", baseline, ActionListener.wrap(ref::set, err::set));
        if (err.get() != null) {
            fail("dryRunContainer failed: " + err.get());
        }
        assertNotNull("dry-run should complete synchronously with mocked client", ref.get());
        return ref.get();
    }

    // =========================================================================================

    @Test
    public void testSessionsUnionTimeAndCount() {
        long old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        MemoryConfiguration config = sessionConfig(7, 5);

        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            String query = q(req);

            if (idx.endsWith("sessions")) {
                if (!isCount(req)) {
                    if (isFetchSourceDisabled(req)) {
                        // collectOldestDocIds: countSet = {s2,s3,s4}; excess requested = 3
                        listener.onResponse(hitsResp(new SearchHit[] { idHit("s2", 1), idHit("s3", 2), idHit("s4", 3) }));
                    } else {
                        // identifyTimeBasedExpiredSessions: timeSet = {s1,s2,s3}
                        listener.onResponse(hitsResp(new SearchHit[] { tsHit("s1", old), tsHit("s2", old), tsHit("s3", old) }));
                    }
                } else if (query.contains("must_not")) {
                    // count non-pinned sessions -> total 8, excess = 3
                    listener.onResponse(countResp(8));
                } else {
                    // pinned count (checkPinnedExceedsMaxCount) or pinned-expiry count -> 0
                    listener.onResponse(countResp(0));
                }
            } else if (idx.endsWith("working")) {
                // cascade count for the expired session union
                listener.onResponse(countResp(10));
            } else {
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        // baseline null -> orphan sweep skipped with a warning
        MemoryRetentionDryRunResult result = run(config, null);

        assertEquals("stored", result.getPolicySource());
        // union {s1,s2,s3,s4} = 4; retention_days owns {s1,s2,s3}=3; max_count owns only {s4}=1
        assertEquals(4, result.getSessions().getTotal());
        assertEquals(Long.valueOf(3L), result.getSessions().getByReason().get("retention_days"));
        assertEquals(Long.valueOf(1L), result.getSessions().getByReason().get("max_count"));
        // cascade working = 10, ttl 0 (session enabled), orphan 0 (baseline null)
        assertEquals(10, result.getWorkingMemory().getTotal());
        assertEquals(Long.valueOf(10L), result.getWorkingMemory().getByReason().get("cascade"));
        assertEquals(Long.valueOf(0L), result.getWorkingMemory().getByReason().get("ttl"));
        assertEquals(Long.valueOf(0L), result.getWorkingMemory().getByReason().get("orphan"));
        // total = sessions 4 + working 10
        assertEquals(14, result.getTotalWouldDelete());
        assertEquals(0, result.getPinnedWouldSkip());
        // orphan-baseline warning present
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("orphan sweep has not yet observed")));
        assertNoDeletes();
    }

    @Test
    public void testSessionsTimeOnlyWithPinnedSkip() {
        long old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        MemoryConfiguration config = sessionConfig(7, null); // time-based only

        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            String query = q(req);
            if (idx.endsWith("sessions")) {
                if (!isCount(req)) {
                    // time-based fetch: 2 expired
                    listener.onResponse(hitsResp(new SearchHit[] { tsHit("s1", old), tsHit("s2", old) }));
                } else if (query.contains("pinned") && query.contains("range")) {
                    // pinned docs inside the time-expiry window -> pinned_would_skip
                    listener.onResponse(countResp(3));
                } else {
                    listener.onResponse(countResp(0));
                }
            } else if (idx.endsWith("working")) {
                listener.onResponse(countResp(4));
            } else {
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, null);

        assertEquals(2, result.getSessions().getTotal());
        assertEquals(Long.valueOf(2L), result.getSessions().getByReason().get("retention_days"));
        assertEquals(Long.valueOf(0L), result.getSessions().getByReason().get("max_count"));
        assertEquals(3, result.getPinnedWouldSkip());
        assertEquals(4, result.getWorkingMemory().getByReason().get("cascade").longValue());
        assertNoDeletes();
    }

    @Test
    public void testLongTermSequentialTimeThenCount() {
        long old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(200);
        java.util.Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.LONG_TERM, new RetentionRule(180, 100)); // days + max_count
        MemoryConfiguration config = fullConfig(policy, false);

        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            String query = q(req);
            if (idx.endsWith("long-term")) {
                if (query.contains("range")) {
                    // time-expired non-pinned count (only this query has an epoch-aware range clause)
                    listener.onResponse(countResp(20));
                } else if (query.contains("must_not")) {
                    // total non-pinned count
                    listener.onResponse(countResp(160));
                } else {
                    // checkPinnedExceedsMaxCount pinned count (pinned as must, no must_not, no range)
                    listener.onResponse(countResp(0));
                }
            } else {
                // sessions/history absent-or-empty, working cascade 0 (no expired sessions)
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, null);

        // time=20; remaining=160-20=140; count excess = 140-100 = 40; total=60
        assertEquals("stored", result.getPolicySource());
        assertEquals(60, result.getLongTerm().getTotal());
        assertEquals(Long.valueOf(20L), result.getLongTerm().getByReason().get("retention_days"));
        assertEquals(Long.valueOf(40L), result.getLongTerm().getByReason().get("max_count"));
        assertNoDeletes();
    }

    @Test
    public void testHistoryCountOnly() {
        java.util.Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.HISTORY, new RetentionRule(null, 5000));
        MemoryConfiguration config = fullConfig(policy, false);

        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            String query = q(req);
            if (idx.endsWith("history")) {
                if (query.contains("must_not")) {
                    // total non-pinned history -> 5008, excess 8
                    listener.onResponse(countResp(5008));
                } else {
                    // pinned count -> 0
                    listener.onResponse(countResp(0));
                }
            } else {
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, null);

        assertEquals(8, result.getHistory().getTotal());
        assertEquals(Long.valueOf(0L), result.getHistory().getByReason().get("retention_days"));
        assertEquals(Long.valueOf(8L), result.getHistory().getByReason().get("max_count"));
        assertNoDeletes();
    }

    @Test
    public void testHistoryPinnedExceedsMaxCountWarning() {
        java.util.Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.HISTORY, new RetentionRule(null, 500));
        MemoryConfiguration config = fullConfig(policy, false);

        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            String query = q(req);
            if (idx.endsWith("history")) {
                if (query.contains("must_not")) {
                    listener.onResponse(countResp(300)); // under max_count -> no count eviction
                } else {
                    listener.onResponse(countResp(600)); // pinned 600 > max_count 500 -> warning
                }
            } else {
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, null);

        assertEquals(0, result.getHistory().getTotal());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("pinned count (600) exceeds max_count (500) for history")));
        assertNoDeletes();
    }

    @Test
    public void testWorkingTtlForSessionlessContainer() {
        long old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
        java.util.Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.HISTORY, new RetentionRule(null, 5000));
        MemoryConfiguration config = fullConfig(policy, true); // disableSession -> TTL path active, sessions index null

        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            String query = q(req);
            if (idx.endsWith("working")) {
                // TTL count (created_time cutoff, epoch-aware)
                listener.onResponse(countResp(34));
            } else if (idx.endsWith("history")) {
                listener.onResponse(query.contains("must_not") ? countResp(5000) : countResp(0));
            } else {
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, null);

        assertEquals(34, result.getWorkingMemory().getByReason().get("ttl").longValue());
        assertEquals(0, result.getWorkingMemory().getByReason().get("cascade").longValue());
        // orphan sweep does not run for session-less containers
        assertEquals(0, result.getWorkingMemory().getByReason().get("orphan").longValue());
        assertEquals(34, result.getWorkingMemory().getTotal());
        assertNoDeletes();
    }

    @Test
    public void testPolicySourceDefaultBackfill() {
        // No stored policy, but admin defaults configured -> policy_source = default
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", false)
            .put("plugins.ml_commons.memory.retention_enabled", true)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.default_session_retention_days", 30)
            .put("plugins.ml_commons.memory.default_session_max_count", 500)
            .put("plugins.ml_commons.memory.default_long_term_max_count", -1)
            .put("plugins.ml_commons.memory.default_history_max_count", -1)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .put("plugins.ml_commons.memory.working_memory_ttl_days", 30)
            .build();
        applySettings(settings);

        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").useSystemIndex(false).disableSession(false).build(); // no
                                                                                                                                            // retention
                                                                                                                                            // policy

        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            if (idx.endsWith("sessions")) {
                if (!isCount(req)) {
                    listener.onResponse(hitsResp(new SearchHit[0])); // no time-expired
                } else {
                    listener.onResponse(countResp(0)); // non-pinned under max_count, pinned 0
                }
            } else {
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, null);
        assertEquals("default", result.getPolicySource());
        assertNotNull(result.getEffectivePolicy());
        assertEquals(Integer.valueOf(30), result.getEffectivePolicy().get(MemoryType.SESSIONS).getRetentionDays());
        assertEquals(Integer.valueOf(500), result.getEffectivePolicy().get(MemoryType.SESSIONS).getMaxCount());
        assertNoDeletes();
    }

    @Test
    public void testPolicySourceNoneWhenNoPolicyAndNoDefaults() {
        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").useSystemIndex(false).disableSession(false).build();

        // No searches should be strictly required, but return empty for any that occur.
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(countResp(0));
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, null);
        assertEquals("none", result.getPolicySource());
        assertEquals(0, result.getTotalWouldDelete());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("no retention policy and no cluster defaults")));
        assertNoDeletes();
    }

    @Test
    public void testRetentionDisabledAndMultiTenancyWarnings() {
        Settings settings = Settings
            .builder()
            .put("plugins.ml_commons.multi_tenancy_enabled", true)
            .put("plugins.ml_commons.memory.retention_enabled", false)
            .put("plugins.ml_commons.memory.retention_job_throttle_seconds", 1)
            .put("plugins.ml_commons.memory.default_session_retention_days", -1)
            .put("plugins.ml_commons.memory.default_session_max_count", -1)
            .put("plugins.ml_commons.memory.default_long_term_max_count", -1)
            .put("plugins.ml_commons.memory.default_history_max_count", -1)
            .put("plugins.ml_commons.memory.orphan_ttl_days", 7)
            .put("plugins.ml_commons.memory.working_memory_ttl_days", 30)
            .build();
        applySettings(settings);

        MemoryConfiguration config = sessionConfig(7, null);
        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            if (idx.endsWith("sessions") && req.source().size() > 0) {
                listener.onResponse(hitsResp(new SearchHit[0]));
            } else {
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, null);
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("multi-tenancy is enabled")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("retention is disabled cluster-wide")));
        assertNoDeletes();
    }

    @Test
    public void testOrphanSweepSkippedWhenSessionsIndexMissing() {
        // Regression: when the working index exists but the sessions index does not, the real orphan sweep skips
        // (else it would classify ALL working memory as orphaned). The dry-run must report orphan=0 too.
        java.util.Map<MemoryType, RetentionRule> policy = new java.util.EnumMap<>(MemoryType.class);
        policy.put(MemoryType.SESSIONS, new RetentionRule(7, null));
        MemoryConfiguration config = sessionConfig(7, null);

        // sessions index does NOT exist; working index does
        ClusterState clusterState = mock(ClusterState.class);
        Metadata metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        lenient().when(metadata.hasIndex(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return !name.endsWith("sessions");
        });

        long veryOldBaseline = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365);

        doAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            String idx = req.indices()[0];
            if (idx.endsWith("sessions") && req.source().size() > 0) {
                listener.onResponse(hitsResp(new SearchHit[0])); // no time-expired sessions
            } else if (idx.endsWith("working")) {
                // If the guard were missing, orphan enumeration would run and count these. It must NOT.
                listener.onResponse(countResp(9999));
            } else {
                listener.onResponse(countResp(0));
            }
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        MemoryRetentionDryRunResult result = run(config, veryOldBaseline);
        assertEquals(
            "orphan sweep must be skipped when sessions index is missing",
            0,
            result.getWorkingMemory().getByReason().get("orphan").longValue()
        );
        assertNoDeletes();
    }

    @Test
    public void testResolveEffectivePolicyStored() {
        MemoryConfiguration config = sessionConfig(7, 5);
        assertEquals("stored", processor.resolveEffectivePolicyForDryRun(config));
    }

    @Test
    public void testComputeDefaultRetentionPolicyNullWhenUnset() {
        // default settings are all -1 in setUp -> null
        assertEquals(null, processor.computeDefaultRetentionPolicy());
    }
}

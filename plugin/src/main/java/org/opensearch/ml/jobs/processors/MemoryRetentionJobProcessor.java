/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DISABLE_SESSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_STORAGE_CONFIG_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ORPHAN_SWEEP_BASELINE_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PINNED_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.RETENTION_POLICY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USER_ID_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_DEFAULT_HISTORY_MAX_COUNT;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_DEFAULT_LONG_TERM_MAX_COUNT;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_DEFAULT_SESSION_MAX_COUNT;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_DEFAULT_SESSION_RETENTION_DAYS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_ORPHAN_TTL_DAYS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_RETENTION_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_RETENTION_JOB_THROTTLE_SECONDS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_WORKING_MEMORY_TTL_DAYS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.memorycontainer.RetentionRule;
import org.opensearch.ml.common.transport.memorycontainer.MemoryRetentionDryRunResult;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.opensearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.opensearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

public class MemoryRetentionJobProcessor extends MLJobProcessor {

    private static final Logger log = LogManager.getLogger(MemoryRetentionJobProcessor.class);

    private static final int CONTAINER_PAGE_SIZE = 100;
    private static final int DELETE_BY_QUERY_BATCH_SIZE = 500;
    private static final int BULK_DELETE_BATCH_SIZE = 1000;
    private static final int ORPHAN_SESSION_ID_CAP = 50_000;
    // Per-run cap on the number of oldest doc IDs collected for count-based eviction (session/long-term/history
    // max-count paths). Without this, a large (totalCount - maxCount) backlog would accumulate an unbounded
    // Set of IDs on the job thread and risk heap exhaustion. Because the query sorts created_time ASC, _id ASC,
    // each run removes the globally-oldest slice and the backlog converges over subsequent scheduled runs.
    // Mirrors ORPHAN_SESSION_ID_CAP.
    private static final int COUNT_BASED_ID_CAP = 50_000;
    private static final int ORPHAN_SESSION_BATCH_SIZE = 1000;
    private static final int ORPHAN_ENUMERATION_PAGE_SIZE = 1000;
    private static final String SESSION_IDS_AGG_NAME = "session_ids";
    private static final String ORPHAN_KEYSPACE_ALPHABET = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";
    private static final int ORPHAN_START_KEY_LENGTH = 4;

    /**
     * Threshold for detecting legacy epoch-SECOND timestamps. Documents written before
     * TransportCreateMemorySessionAction switched to toEpochMilli() stored created_time /
     * last_updated_time as epoch seconds (e.g. 1752400000), which compared against
     * System.currentTimeMillis() looks like January 1970 and would be evicted immediately.
     *
     * Any value below 10 billion is assumed to be epoch seconds:
     * - 10 billion SECONDS is ~year 2286, so no real epoch-second timestamp exceeds it for centuries.
     * - 10 billion MILLIS is ~1970-04-26, so no real epoch-millis timestamp for actual data is this small.
     */
    private static final long EPOCH_SECOND_DETECTION_THRESHOLD = 10_000_000_000L;

    private static volatile MemoryRetentionJobProcessor instance;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public static MemoryRetentionJobProcessor getInstance(ClusterService clusterService, Client client, ThreadPool threadPool) {
        if (instance != null) {
            return instance;
        }

        synchronized (MemoryRetentionJobProcessor.class) {
            if (instance != null) {
                return instance;
            }

            instance = new MemoryRetentionJobProcessor(clusterService, client, threadPool);
            return instance;
        }
    }

    @VisibleForTesting
    public static synchronized void reset() {
        instance = null;
    }

    public MemoryRetentionJobProcessor(ClusterService clusterService, Client client, ThreadPool threadPool) {
        super(clusterService, client, threadPool);
    }

    @Override
    public void run() {
        if (clusterService.getClusterSettings().get(ML_COMMONS_MULTI_TENANCY_ENABLED)) {
            log.warn("Memory retention job skipped: multi-tenancy is enabled and native client lacks tenant routing");
            return;
        }

        if (!clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_RETENTION_ENABLED)) {
            log.info("Memory retention job disabled via cluster setting plugins.ml_commons.memory.retention_enabled=false");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Memory retention job already in progress, skipping this invocation");
            return;
        }

        try {
            log.info("Memory retention job started");
            resolveContainersWithPolicies(null);
        } catch (Exception e) {
            log.error("Unexpected error starting memory retention job", e);
            isRunning.set(false);
        }
    }

    private void resolveContainersWithPolicies(Object[] searchAfterValues) {
        SearchRequest request = new SearchRequest(ML_MEMORY_CONTAINER_INDEX);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery())
            .size(CONTAINER_PAGE_SIZE)
            .sort("_id", SortOrder.ASC)
            .fetchSource(true);

        if (searchAfterValues != null) {
            sourceBuilder.searchAfter(searchAfterValues);
        }

        request.source(sourceBuilder);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(request, ActionListener.wrap(response -> {
                SearchHits hits = response.getHits();
                SearchHit[] hitArray = hits.getHits();

                if (hitArray.length == 0) {
                    onJobComplete();
                    return;
                }

                processContainerChain(
                    hitArray,
                    0,
                    hitArray.length == CONTAINER_PAGE_SIZE ? hitArray[hitArray.length - 1].getSortValues() : null
                );
            }, e -> {
                log.error("Failed to search for containers with retention policies", e);
                onJobComplete();
            }));
        }
    }

    private void processContainerChain(SearchHit[] hits, int index, Object[] nextPageSortValues) {
        if (index >= hits.length) {
            if (nextPageSortValues != null) {
                resolveContainersWithPolicies(nextPageSortValues);
            } else {
                executeOrphanSweep();
            }
            return;
        }

        processContainer(hits[index], ActionListener.wrap(hadDeletions -> {
            if (hadDeletions) {
                scheduleNext(hits, index + 1, nextPageSortValues);
            } else {
                processContainerChain(hits, index + 1, nextPageSortValues);
            }
        }, e -> {
            log.error("Failed processing container [{}]", hits[index].getId(), e);
            processContainerChain(hits, index + 1, nextPageSortValues);
        }));
    }

    private void scheduleNext(SearchHit[] hits, int nextIndex, Object[] nextPageSortValues) {
        int throttleSeconds = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_RETENTION_JOB_THROTTLE_SECONDS);
        try {
            threadPool
                .schedule(
                    () -> processContainerChain(hits, nextIndex, nextPageSortValues),
                    TimeValue.timeValueSeconds(throttleSeconds),
                    ThreadPool.Names.GENERIC
                );
        } catch (Exception e) {
            log.error("Failed to schedule next container processing", e);
            isRunning.set(false);
        }
    }

    private void processContainer(SearchHit hit, ActionListener<Boolean> listener) {
        String containerId = hit.getId();
        try {
            Map<String, Object> source = hit.getSourceAsMap();
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) source.get(MEMORY_STORAGE_CONFIG_FIELD);

            if (configMap == null) {
                log.warn("Container [{}] has no configuration field, skipping", containerId);
                listener.onResponse(false);
                return;
            }

            XContentBuilder xContentBuilder = XContentFactory.jsonBuilder().map(configMap);
            BytesReference bytes = BytesReference.bytes(xContentBuilder);
            MemoryConfiguration config;
            try (
                XContentParser parser = XContentHelper
                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, bytes, XContentType.JSON)
            ) {
                parser.nextToken();
                config = MemoryConfiguration.parse(parser);
            }

            Map<MemoryType, RetentionRule> retentionPolicy = config.getRetentionPolicy();
            if (retentionPolicy == null || retentionPolicy.isEmpty()) {
                if (config.isRetentionPolicyExplicitlyNull()) {
                    log.debug("Container [{}] explicitly opted out of retention, skipping", containerId);
                    listener.onResponse(false);
                    return;
                }
                applyDefaultRetentionPolicy(config, containerId, ActionListener.wrap(persisted -> {
                    if (persisted) {
                        executeRetentionPipeline(config, containerId, listener);
                    } else {
                        listener.onResponse(false);
                    }
                }, listener::onFailure));
            } else {
                executeRetentionPipeline(config, containerId, listener);
            }
        } catch (Exception e) {
            log.error("Error parsing configuration for container [{}]", containerId, e);
            listener.onResponse(false);
        }
    }

    private void executeRetentionPipeline(MemoryConfiguration config, String containerId, ActionListener<Boolean> listener) {
        executeSessionRetention(
            config,
            containerId,
            ActionListener
                .wrap(
                    sessionsDeleted -> executeLongTermRetention(
                        config,
                        containerId,
                        ActionListener
                            .wrap(
                                longTermDeleted -> executeHistoryRetention(
                                    config,
                                    containerId,
                                    ActionListener
                                        .wrap(
                                            historyDeleted -> executeWorkingMemoryTTL(
                                                config,
                                                containerId,
                                                ActionListener
                                                    .wrap(
                                                        workingDeleted -> listener
                                                            .onResponse(
                                                                sessionsDeleted || longTermDeleted || historyDeleted || workingDeleted
                                                            ),
                                                        listener::onFailure
                                                    )
                                            ),
                                            listener::onFailure
                                        )
                                ),
                                listener::onFailure
                            )
                    ),
                    listener::onFailure
                )
        );
    }

    /**
     * Backfills the cluster-default retention policy onto a container that has none, using a
     * conditional ("if-absent") script update so a concurrently user-set policy or opt-out is
     * never clobbered. Responds {@code true} only if the default policy was actually persisted;
     * {@code false} means this container must be skipped for this run (no admin default retention
     * settings are configured, a concurrent user write won, or the persist failed) so deletions
     * never happen under a policy the user cannot see. Only settings explicitly configured by the
     * admin (value &gt; 0; the settings default to -1, an unset sentinel) contribute to the policy.
     */
    /**
     * Computes the cluster-default retention policy from admin settings, or {@code null} when no admin
     * defaults are configured (all settings at the -1 unset sentinel). Only settings with a value &gt; 0
     * contribute a rule. This is the single source of truth for the default policy, shared by the real
     * backfill path ({@link #applyDefaultRetentionPolicy}) and the dry-run so the two can never diverge.
     */
    @VisibleForTesting
    Map<MemoryType, RetentionRule> computeDefaultRetentionPolicy() {
        int sessionRetentionDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_DEFAULT_SESSION_RETENTION_DAYS);
        int sessionMaxCount = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_DEFAULT_SESSION_MAX_COUNT);
        int longTermMaxCount = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_DEFAULT_LONG_TERM_MAX_COUNT);
        int historyMaxCount = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_DEFAULT_HISTORY_MAX_COUNT);

        boolean anyConfigured = sessionRetentionDays > 0 || sessionMaxCount > 0 || longTermMaxCount > 0 || historyMaxCount > 0;
        if (!anyConfigured) {
            return null;
        }

        Map<MemoryType, RetentionRule> defaultPolicy = new EnumMap<>(MemoryType.class);
        if (sessionRetentionDays > 0 || sessionMaxCount > 0) {
            defaultPolicy
                .put(
                    MemoryType.SESSIONS,
                    new RetentionRule(sessionRetentionDays > 0 ? sessionRetentionDays : null, sessionMaxCount > 0 ? sessionMaxCount : null)
                );
        }
        if (longTermMaxCount > 0) {
            defaultPolicy.put(MemoryType.LONG_TERM, new RetentionRule(null, longTermMaxCount));
        }
        if (historyMaxCount > 0) {
            defaultPolicy.put(MemoryType.HISTORY, new RetentionRule(null, historyMaxCount));
        }
        return defaultPolicy;
    }

    private void applyDefaultRetentionPolicy(MemoryConfiguration config, String containerId, ActionListener<Boolean> listener) {
        Map<MemoryType, RetentionRule> defaultPolicy = computeDefaultRetentionPolicy();
        if (defaultPolicy == null) {
            log
                .debug(
                    "Container [{}] has no retention policy and no admin default retention settings are configured; skipping",
                    containerId
                );
            listener.onResponse(false);
            return;
        }

        config.setRetentionPolicy(defaultPolicy);

        // Derive the logged interval/counts from the computed policy rather than re-reading cluster settings
        // (computeDefaultRetentionPolicy already read them). A null rule/value means that type has no default.
        RetentionRule sessionsRule = defaultPolicy.get(MemoryType.SESSIONS);
        RetentionRule longTermRule = defaultPolicy.get(MemoryType.LONG_TERM);
        RetentionRule historyRule = defaultPolicy.get(MemoryType.HISTORY);
        log
            .info(
                "[MemoryRetentionJob] container={} auto-applying default retention policy: sessions={}/{}d, long-term={}, history={}",
                containerId,
                sessionsRule != null ? sessionsRule.getMaxCount() : null,
                sessionsRule != null ? sessionsRule.getRetentionDays() : null,
                longTermRule != null ? longTermRule.getMaxCount() : null,
                historyRule != null ? historyRule.getMaxCount() : null
            );

        try {
            XContentBuilder policyBuilder = XContentFactory.jsonBuilder().startObject();
            for (Map.Entry<MemoryType, RetentionRule> entry : defaultPolicy.entrySet()) {
                policyBuilder.field(entry.getKey().getValue());
                entry.getValue().toXContent(policyBuilder, null);
            }
            policyBuilder.endObject();
            Map<String, Object> policyMap = XContentHelper.convertToMap(BytesReference.bytes(policyBuilder), false, XContentType.JSON).v2();

            // Conditional write: only backfill if the container still has no retention_policy key.
            // A present key (even with a null value, i.e. an explicit opt-out) means a user write
            // happened concurrently and takes precedence, so the update becomes a noop.
            String scriptSource = "Map cfg = (Map) ctx._source.get(params.configField);"
                + " if (cfg == null || cfg.containsKey(params.policyField)) { ctx.op = 'noop'; }"
                + " else { cfg.put(params.policyField, params.policy); }";
            Map<String, Object> scriptParams = Map
                .of("configField", MEMORY_STORAGE_CONFIG_FIELD, "policyField", RETENTION_POLICY_FIELD, "policy", policyMap);

            UpdateRequest updateRequest = new UpdateRequest(ML_MEMORY_CONTAINER_INDEX, containerId)
                .script(new Script(ScriptType.INLINE, "painless", scriptSource, scriptParams))
                .retryOnConflict(3);

            try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
                client.update(updateRequest, ActionListener.wrap(response -> {
                    if (response.getResult() == DocWriteResponse.Result.NOOP) {
                        log.debug("Container [{}] retention policy was set concurrently by a user; skipping default backfill", containerId);
                        listener.onResponse(false);
                        return;
                    }
                    log.debug("Successfully persisted default retention policy on container [{}]", containerId);
                    listener.onResponse(true);
                }, e -> {
                    if (ExceptionsHelper.unwrapCause(e) instanceof VersionConflictEngineException) {
                        log
                            .debug(
                                "Conflict persisting default retention policy on container [{}]; user's policy takes precedence",
                                containerId,
                                e
                            );
                    } else {
                        log
                            .warn(
                                "Failed to persist default retention policy on container [{}], skipping retention for this run",
                                containerId,
                                e
                            );
                    }
                    listener.onResponse(false);
                }));
            }
        } catch (Exception e) {
            log.warn("Failed to build default retention policy for container [{}], skipping retention for this run", containerId, e);
            listener.onResponse(false);
        }
    }

    private void executeSessionRetention(MemoryConfiguration config, String containerId, ActionListener<Boolean> listener) {
        String sessionIndex = config.getIndexName(MemoryType.SESSIONS);
        if (sessionIndex == null) {
            log.debug("Session index is null for container [{}], skipping session retention", containerId);
            listener.onResponse(false);
            return;
        }

        Map<MemoryType, RetentionRule> retentionPolicy = config.getRetentionPolicy();
        if (retentionPolicy == null || !retentionPolicy.containsKey(MemoryType.SESSIONS)) {
            listener.onResponse(false);
            return;
        }

        RetentionRule sessionRule = retentionPolicy.get(MemoryType.SESSIONS);
        if (sessionRule == null) {
            listener.onResponse(false);
            return;
        }

        identifyExpiredSessions(config, containerId, sessionIndex, sessionRule, ActionListener.wrap(expiredSessionIds -> {
            if (expiredSessionIds.isEmpty()) {
                log.info("[MemoryRetentionJob] container={} sessions_expired=0", containerId);
                listener.onResponse(false);
                return;
            }

            log.info("[MemoryRetentionJob] container={} sessions_expired={}", containerId, expiredSessionIds.size());

            cascadeDeleteWorkingMemory(
                config,
                containerId,
                expiredSessionIds,
                ActionListener
                    .wrap(
                        deletedCount -> deleteSessionDocuments(
                            config,
                            sessionIndex,
                            expiredSessionIds,
                            ActionListener.wrap(v -> listener.onResponse(true), listener::onFailure)
                        ),
                        listener::onFailure
                    )
            );
        }, listener::onFailure));
    }

    private void identifyExpiredSessions(
        MemoryConfiguration config,
        String containerId,
        String sessionIndex,
        RetentionRule rule,
        ActionListener<Set<String>> listener
    ) {
        Set<String> expiredIds = new HashSet<>();

        ActionListener<Set<String>> timeBasedDone = ActionListener.wrap(timeExpired -> {
            expiredIds.addAll(timeExpired);
            if (rule.getMaxCount() != null) {
                identifyCountBasedExpiredSessions(
                    config,
                    containerId,
                    sessionIndex,
                    rule.getMaxCount(),
                    ActionListener.wrap(countExpired -> {
                        expiredIds.addAll(countExpired);
                        listener.onResponse(expiredIds);
                    }, listener::onFailure)
                );
            } else {
                listener.onResponse(expiredIds);
            }
        }, listener::onFailure);

        if (rule.getRetentionDays() != null) {
            identifyTimeBasedExpiredSessions(config, containerId, sessionIndex, rule.getRetentionDays(), timeBasedDone);
        } else {
            timeBasedDone.onResponse(new HashSet<>());
        }
    }

    private void identifyTimeBasedExpiredSessions(
        MemoryConfiguration config,
        String containerId,
        String sessionIndex,
        int retentionDays,
        ActionListener<Set<String>> listener
    ) {
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);

        SearchRequest request = new SearchRequest(sessionIndex);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        // NOTE: legacy sessions written before the epoch-millis fix stored last_updated_time as epoch
        // SECONDS, which always satisfies lt(cutoffMillis) and would make every pre-upgrade session look
        // expired. The query therefore only pre-filters candidates; each hit's timestamp is fetched and
        // re-verified client-side via normalizeTimestamp() before the session is marked expired.
        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .must(QueryBuilders.rangeQuery(LAST_UPDATED_TIME_FIELD).lt(cutoffMillis))
            .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(query)
            .size(CONTAINER_PAGE_SIZE)
            .sort("_id", SortOrder.ASC)
            .fetchSource(new String[] { LAST_UPDATED_TIME_FIELD }, null);

        request.source(sourceBuilder);

        Set<String> expiredIds = new HashSet<>();
        searchAllPages(request, containerId, cutoffMillis, new AtomicBoolean(false), expiredIds, listener);
    }

    private void searchAllPages(
        SearchRequest request,
        String containerId,
        long cutoffMillis,
        AtomicBoolean epochSecondWarned,
        Set<String> accumulator,
        ActionListener<Set<String>> listener
    ) {
        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(request, ActionListener.wrap(response -> {
                SearchHit[] hits = response.getHits().getHits();
                for (SearchHit hit : hits) {
                    if (isSessionExpired(hit, containerId, cutoffMillis, epochSecondWarned)) {
                        accumulator.add(hit.getId());
                    }
                }

                if (hits.length == CONTAINER_PAGE_SIZE) {
                    Object[] sortValues = hits[hits.length - 1].getSortValues();
                    request.source().searchAfter(sortValues);
                    searchAllPages(request, containerId, cutoffMillis, epochSecondWarned, accumulator, listener);
                } else {
                    listener.onResponse(accumulator);
                }
            }, listener::onFailure));
        }
    }

    /**
     * Re-verifies a session hit against the retention cutoff using a normalized (epoch-millis)
     * timestamp. Legacy documents that stored last_updated_time in epoch seconds match the range
     * query unconditionally, so this client-side check is what prevents pre-upgrade sessions from
     * being mass-evicted. If the timestamp cannot be read from the hit source, the session is
     * SKIPPED (not deleted) to avoid data loss from unparseable metadata.
     */
    private boolean isSessionExpired(SearchHit hit, String containerId, long cutoffMillis, AtomicBoolean epochSecondWarned) {
        Long lastUpdated = extractTimestamp(hit, LAST_UPDATED_TIME_FIELD);
        if (lastUpdated == null) {
            log
                .warn(
                    "[MemoryRetentionJob] container={} session={} has unparseable/missing last_updated_time; skipping",
                    containerId,
                    hit.getId()
                );
            return false;
        }

        long normalized = normalizeTimestamp(lastUpdated);
        if (normalized != lastUpdated && epochSecondWarned.compareAndSet(false, true)) {
            log
                .warn(
                    "[MemoryRetentionJob] container={} detected epoch-second timestamp on session {},"
                        + " converting to millis for comparison. Consider running timestamp migration.",
                    containerId,
                    hit.getId()
                );
        }
        return normalized < cutoffMillis;
    }

    /**
     * Extracts a timestamp field from a hit's source, tolerating the numeric types the source map
     * may deserialize to (Integer, Long, Double) as well as numeric strings. Returns {@code null}
     * when the source or field is unavailable.
     */
    private static Long extractTimestamp(SearchHit hit, String field) {
        Map<String, Object> source = hit.getSourceAsMap();
        if (source == null) {
            return null;
        }
        Object value = source.get(field);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Normalizes a stored timestamp to epoch milliseconds. Legacy documents (written before
     * TransportCreateMemorySessionAction switched to toEpochMilli()) stored timestamps in epoch
     * SECONDS; comparing those against System.currentTimeMillis() makes them look like January 1970
     * and would evict all pre-upgrade data on the first job run. Any value below
     * {@link #EPOCH_SECOND_DETECTION_THRESHOLD} is assumed to be epoch seconds and converted.
     *
     * @param timestamp a stored created_time / last_updated_time value (epoch seconds or millis)
     * @return the timestamp in epoch milliseconds
     */
    private static long normalizeTimestamp(long timestamp) {
        if (timestamp > 0 && timestamp < EPOCH_SECOND_DETECTION_THRESHOLD) {
            return timestamp * 1000;
        }
        return timestamp;
    }

    /**
     * Builds an "expired before cutoff" query that applies the {@link #normalizeTimestamp(long)}
     * heuristic at the query level, for delete-by-query paths where per-document client-side
     * normalization is not possible. A document is expired when either:
     * <ul>
     *   <li>epoch-millis scale ({@code >= EPOCH_SECOND_DETECTION_THRESHOLD}) and {@code < cutoffMillis}, or</li>
     *   <li>legacy epoch-seconds scale ({@code < EPOCH_SECOND_DETECTION_THRESHOLD}) and
     *       {@code value * 1000 < cutoffMillis}, i.e. {@code value < cutoffMillis / 1000}.</li>
     * </ul>
     * A plain {@code lt(cutoffMillis)} range would match every legacy epoch-second document and
     * mass-delete pre-upgrade data.
     */
    private static BoolQueryBuilder buildEpochAwareCutoffQuery(String timeField, long cutoffMillis) {
        long secondsScaleCutoff = Math.min(cutoffMillis / 1000, EPOCH_SECOND_DETECTION_THRESHOLD);
        return QueryBuilders
            .boolQuery()
            .should(QueryBuilders.rangeQuery(timeField).gte(EPOCH_SECOND_DETECTION_THRESHOLD).lt(cutoffMillis))
            .should(QueryBuilders.rangeQuery(timeField).lt(secondsScaleCutoff))
            .minimumShouldMatch(1);
    }

    /**
     * Matches working-memory documents belonging to any of the given session IDs. Prefers the
     * top-level {@code session_id} keyword field but also matches on the legacy
     * {@code namespace.session_id} flat_object subpath for pre-upgrade documents that lack the
     * top-level field.
     */
    private static BoolQueryBuilder buildSessionIdMatchQuery(List<String> sessionIds) {
        return QueryBuilders
            .boolQuery()
            .should(QueryBuilders.termsQuery(SESSION_ID_FIELD, sessionIds))
            .should(QueryBuilders.termsQuery(NAMESPACE_FIELD + "." + SESSION_ID_FIELD, sessionIds))
            .minimumShouldMatch(1);
    }

    private void identifyCountBasedExpiredSessions(
        MemoryConfiguration config,
        String containerId,
        String sessionIndex,
        int maxCount,
        ActionListener<Set<String>> listener
    ) {
        // Check if pinned count alone exceeds max_count (fire-and-forget warning)
        checkPinnedExceedsMaxCount(sessionIndex, containerId, "sessions", maxCount);

        // Step 1: count non-pinned sessions
        SearchRequest countRequest = new SearchRequest(sessionIndex);
        countRequest.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder countQuery = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true));

        SearchSourceBuilder countSource = new SearchSourceBuilder().query(countQuery).size(0).trackTotalHits(true);
        countRequest.source(countSource);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(countRequest, ActionListener.wrap(countResponse -> {
                long totalCount = countResponse.getHits().getTotalHits().value();
                if (totalCount <= maxCount) {
                    listener.onResponse(new HashSet<>());
                    return;
                }

                int excess = (int) (totalCount - maxCount);
                // Step 2: evict the least-recently-updated excess sessions (LRU). last_updated_time is bumped
                // on every message add, so this keeps the most recently active sessions and drops stale ones.
                // Tie-broken by _id via search_after (see OldestDocIdsPager), so identical timestamps are safe.
                collectOldestDocIds(sessionIndex, containerId, LAST_UPDATED_TIME_FIELD, excess, listener);
            }, listener::onFailure));
        }
    }

    private void cascadeDeleteWorkingMemory(
        MemoryConfiguration config,
        String containerId,
        Set<String> expiredSessionIds,
        ActionListener<Long> listener
    ) {
        String workingMemoryIndex = config.getIndexName(MemoryType.WORKING);
        if (workingMemoryIndex == null) {
            listener.onResponse(0L);
            return;
        }

        List<List<String>> batches = partition(new ArrayList<>(expiredSessionIds), DELETE_BY_QUERY_BATCH_SIZE);
        cascadeDeleteBatch(config, workingMemoryIndex, containerId, batches, 0, 0L, listener);
    }

    private void cascadeDeleteBatch(
        MemoryConfiguration config,
        String workingMemoryIndex,
        String containerId,
        List<List<String>> batches,
        int batchIndex,
        long totalDeleted,
        ActionListener<Long> listener
    ) {
        if (batchIndex >= batches.size()) {
            log.info("[MemoryRetentionJob] container={} cascade_working_memory_deleted={}", containerId, totalDeleted);
            listener.onResponse(totalDeleted);
            return;
        }

        List<String> batch = batches.get(batchIndex);
        if (log.isDebugEnabled()) {
            log.debug("[MemoryRetentionJob] container={} cascade deleting working memory for session ids={}", containerId, batch);
        }

        DeleteByQueryRequest dbq = new DeleteByQueryRequest(workingMemoryIndex);
        dbq.setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        dbq
            .setQuery(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
                    .must(buildSessionIdMatchQuery(batch))
            );
        dbq.setRefresh(true);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.execute(DeleteByQueryAction.INSTANCE, dbq, ActionListener.wrap((BulkByScrollResponse bulkResponse) -> {
                long deleted = bulkResponse.getDeleted();
                cascadeDeleteBatch(config, workingMemoryIndex, containerId, batches, batchIndex + 1, totalDeleted + deleted, listener);
            }, listener::onFailure));
        }
    }

    private void deleteSessionDocuments(
        MemoryConfiguration config,
        String sessionIndex,
        Set<String> expiredSessionIds,
        ActionListener<Void> listener
    ) {
        List<List<String>> batches = partition(new ArrayList<>(expiredSessionIds), BULK_DELETE_BATCH_SIZE);
        deleteSessionBatch(config, sessionIndex, batches, 0, listener);
    }

    private void deleteSessionBatch(
        MemoryConfiguration config,
        String sessionIndex,
        List<List<String>> batches,
        int batchIndex,
        ActionListener<Void> listener
    ) {
        if (batchIndex >= batches.size()) {
            listener.onResponse(null);
            return;
        }

        List<String> batch = batches.get(batchIndex);
        if (log.isDebugEnabled()) {
            log.debug("[MemoryRetentionJob] index={} deleting session ids={}", sessionIndex, batch);
        }
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        for (String sessionId : batch) {
            bulkRequest.add(new DeleteRequest(sessionIndex, sessionId));
        }

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.bulk(bulkRequest, ActionListener.wrap(bulkResponse -> {
                if (bulkResponse.hasFailures()) {
                    String failureMsg = bulkResponse.buildFailureMessage();
                    log
                        .warn(
                            "[MemoryRetentionJob] Partial session delete failures: {}",
                            failureMsg.length() > 500 ? failureMsg.substring(0, 500) + "..." : failureMsg
                        );
                }
                deleteSessionBatch(config, sessionIndex, batches, batchIndex + 1, listener);
            }, listener::onFailure));
        }
    }

    private void executeLongTermRetention(MemoryConfiguration config, String containerId, ActionListener<Boolean> listener) {
        String longTermIndex = config.getIndexName(MemoryType.LONG_TERM);
        if (longTermIndex == null) {
            listener.onResponse(false);
            return;
        }

        Map<MemoryType, RetentionRule> retentionPolicy = config.getRetentionPolicy();
        if (retentionPolicy == null || !retentionPolicy.containsKey(MemoryType.LONG_TERM)) {
            listener.onResponse(false);
            return;
        }

        RetentionRule rule = retentionPolicy.get(MemoryType.LONG_TERM);
        if (rule == null) {
            listener.onResponse(false);
            return;
        }

        ActionListener<Long> timeBasedDone = ActionListener.wrap(timeDeleted -> {
            if (rule.getMaxCount() != null) {
                executeLongTermMaxCount(longTermIndex, containerId, rule.getMaxCount(), ActionListener.wrap(countDeleted -> {
                    long total = (timeDeleted != null ? timeDeleted : 0L) + countDeleted;
                    log.info("[MemoryRetentionJob] container={} long_term_deleted={}", containerId, total);
                    listener.onResponse(total > 0);
                }, listener::onFailure));
            } else {
                long total = timeDeleted != null ? timeDeleted : 0L;
                log.info("[MemoryRetentionJob] container={} long_term_deleted={}", containerId, total);
                listener.onResponse(total > 0);
            }
        }, listener::onFailure);

        if (rule.getRetentionDays() != null) {
            executeLongTermRetentionDays(longTermIndex, containerId, rule.getRetentionDays(), timeBasedDone);
        } else {
            timeBasedDone.onResponse(0L);
        }
    }

    private void executeLongTermRetentionDays(String longTermIndex, String containerId, int retentionDays, ActionListener<Long> listener) {
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        log
            .debug(
                "[MemoryRetentionJob] container={} long_term retention_days DBQ index={} cutoffMillis={}",
                containerId,
                longTermIndex,
                cutoffMillis
            );

        DeleteByQueryRequest dbq = new DeleteByQueryRequest(longTermIndex);
        dbq.setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        // Epoch-aware cutoff: legacy docs store last_updated_time in epoch seconds and would all
        // match a plain lt(cutoffMillis) range. See normalizeTimestamp().
        dbq
            .setQuery(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
                    .must(buildEpochAwareCutoffQuery(LAST_UPDATED_TIME_FIELD, cutoffMillis))
                    .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true))
            );
        dbq.setRefresh(true);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.execute(DeleteByQueryAction.INSTANCE, dbq, ActionListener.wrap((BulkByScrollResponse response) -> {
                listener.onResponse(response.getDeleted());
            }, listener::onFailure));
        }
    }

    private void executeLongTermMaxCount(String longTermIndex, String containerId, int maxCount, ActionListener<Long> listener) {
        // Check if pinned count alone exceeds max_count (fire-and-forget warning)
        checkPinnedExceedsMaxCount(longTermIndex, containerId, "long_term", maxCount);

        // Step 1: count non-pinned long-term docs
        SearchRequest countRequest = new SearchRequest(longTermIndex);
        countRequest.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder countQuery = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true));

        SearchSourceBuilder countSource = new SearchSourceBuilder().query(countQuery).size(0).trackTotalHits(true);
        countRequest.source(countSource);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(countRequest, ActionListener.wrap(countResponse -> {
                long totalCount = countResponse.getHits().getTotalHits().value();
                if (totalCount <= maxCount) {
                    listener.onResponse(0L);
                    return;
                }

                int excess = (int) (totalCount - maxCount);
                // Step 2: evict the least-recently-updated excess docs (LRU). last_updated_time advances when a
                // long-term memory is updated, so this keeps the most recently touched facts and drops stale ones.
                // Tie-broken by _id via search_after (see OldestDocIdsPager), so identical timestamps are safe.
                collectOldestDocIds(longTermIndex, containerId, LAST_UPDATED_TIME_FIELD, excess, ActionListener.wrap(idsToDelete -> {
                    if (idsToDelete.isEmpty()) {
                        listener.onResponse(0L);
                        return;
                    }
                    // Step 3: bulk delete those IDs
                    deleteDocumentsBatch(longTermIndex, new ArrayList<>(idsToDelete), listener);
                }, listener::onFailure));
            }, listener::onFailure));
        }
    }

    private void collectOldestDocIds(String index, String containerId, String timeField, int limit, ActionListener<Set<String>> listener) {
        int cappedLimit = Math.min(limit, COUNT_BASED_ID_CAP);
        if (limit > COUNT_BASED_ID_CAP) {
            log
                .warn(
                    "Count-based eviction for container [{}] on index [{}] requested {} docs, capping at {} this run; remaining covered in future runs.",
                    containerId,
                    index,
                    limit,
                    COUNT_BASED_ID_CAP
                );
        }
        Set<String> collected = new HashSet<>();
        new OldestDocIdsPager(index, containerId, timeField, cappedLimit, collected, listener).start();
    }

    /**
     * Pages through the oldest (timeField ASC, _id ASC) non-pinned docs of a container via search_after, collecting up
     * to {@code limit} doc IDs. Formerly this paging was expressed as a method that recursed into itself from inside the
     * search callback; with a synchronous client (e.g. a mocked client in unit tests) that produced real on-stack
     * recursion of up to ~500 frames (COUNT_BASED_ID_CAP 50k / CONTAINER_PAGE_SIZE 100), which overflowed the stack on
     * smaller CI stack sizes. This driver replaces the recursion with a work-in-progress (wip) serialized drain: page
     * fetches are issued from a single {@link #drain()} loop, so paging uses O(1) stack whether the search callback
     * fires synchronously (same thread) or asynchronously (real client on another thread), and it is free of
     * lost-wakeup races. Behavior is otherwise identical: same final {@code collected} set, same cap enforcement
     * (never more than {@code limit}), same stop conditions ({@code hits.length < pageSize} or
     * {@code collected.size() >= limit}), same error propagation via the caller's {@code listener::onFailure}.
     */
    private final class OldestDocIdsPager {
        private final String index;
        private final String containerId;
        private final String timeField;
        private final int limit;
        private final Set<String> collected;
        private final ActionListener<Set<String>> listener;

        private final AtomicInteger wip = new AtomicInteger(0);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private volatile Object[] searchAfter = null;

        OldestDocIdsPager(
            String index,
            String containerId,
            String timeField,
            int limit,
            Set<String> collected,
            ActionListener<Set<String>> listener
        ) {
            this.index = index;
            this.containerId = containerId;
            this.timeField = timeField;
            this.limit = limit;
            this.collected = collected;
            this.listener = listener;
        }

        void start() {
            drain();
        }

        /**
         * Exclusive driver loop. Only one thread runs the body at a time (guarded by the wip counter); any callback
         * that arrives while the loop is running simply increments wip so the loop issues the next page, and a callback
         * that arrives after the loop has parked (wip drained to 0) resumes the loop on its own thread. Either way the
         * next page is fetched from this single frame rather than nested inside the previous page's callback.
         */
        private void drain() {
            if (wip.getAndIncrement() != 0) {
                return;
            }
            int missed = 1;
            for (;;) {
                if (done.get()) {
                    return;
                }
                fetchNextPage();
                missed = wip.addAndGet(-missed);
                if (missed == 0) {
                    return;
                }
            }
        }

        private void fetchNextPage() {
            SearchRequest request = new SearchRequest(index);
            request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

            BoolQueryBuilder query = QueryBuilders
                .boolQuery()
                .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
                .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true));

            int pageSize = Math.min(CONTAINER_PAGE_SIZE, limit - collected.size());
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(query)
                .size(pageSize)
                .sort(timeField, SortOrder.ASC)
                .sort("_id", SortOrder.ASC)
                .fetchSource(false);

            if (searchAfter != null) {
                sourceBuilder.searchAfter(searchAfter);
            }

            request.source(sourceBuilder);

            try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
                client.search(request, ActionListener.wrap(response -> {
                    SearchHit[] hits = response.getHits().getHits();
                    for (SearchHit hit : hits) {
                        collected.add(hit.getId());
                        if (collected.size() >= limit) {
                            complete();
                            return;
                        }
                    }

                    if (hits.length == pageSize && collected.size() < limit) {
                        searchAfter = hits[hits.length - 1].getSortValues();
                        // Do not recurse here; hand control back to drain() so the next page is fetched on a single
                        // frame. If drain() has parked, this re-enters it on the current (async) thread.
                        drain();
                    } else {
                        complete();
                    }
                }, e -> {
                    done.set(true);
                    listener.onFailure(e);
                }));
            }
        }

        private void complete() {
            done.set(true);
            listener.onResponse(collected);
        }
    }

    private void deleteDocumentsBatch(String index, List<String> ids, ActionListener<Long> listener) {
        List<List<String>> batches = partition(ids, BULK_DELETE_BATCH_SIZE);
        deleteDocumentsBatchRecursive(index, batches, 0, 0L, listener);
    }

    private void deleteDocumentsBatchRecursive(
        String index,
        List<List<String>> batches,
        int batchIndex,
        long totalDeleted,
        ActionListener<Long> listener
    ) {
        if (batchIndex >= batches.size()) {
            listener.onResponse(totalDeleted);
            return;
        }

        List<String> batch = batches.get(batchIndex);
        if (log.isDebugEnabled()) {
            log.debug("[MemoryRetentionJob] index={} deleting doc ids={}", index, batch);
        }
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        for (String docId : batch) {
            bulkRequest.add(new DeleteRequest(index, docId));
        }

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.bulk(bulkRequest, ActionListener.wrap(bulkResponse -> {
                long batchDeleted = Arrays.stream(bulkResponse.getItems()).filter(i -> !i.isFailed()).count();
                if (bulkResponse.hasFailures()) {
                    String failureMsg = bulkResponse.buildFailureMessage();
                    log
                        .warn(
                            "[MemoryRetentionJob] Partial bulk delete failures: {}",
                            failureMsg.length() > 500 ? failureMsg.substring(0, 500) + "..." : failureMsg
                        );
                }
                deleteDocumentsBatchRecursive(index, batches, batchIndex + 1, totalDeleted + batchDeleted, listener);
            }, listener::onFailure));
        }
    }

    private void executeHistoryRetention(MemoryConfiguration config, String containerId, ActionListener<Boolean> listener) {
        String historyIndex = config.getIndexName(MemoryType.HISTORY);
        if (historyIndex == null) {
            listener.onResponse(false);
            return;
        }

        Map<MemoryType, RetentionRule> retentionPolicy = config.getRetentionPolicy();
        if (retentionPolicy == null || !retentionPolicy.containsKey(MemoryType.HISTORY)) {
            listener.onResponse(false);
            return;
        }

        RetentionRule rule = retentionPolicy.get(MemoryType.HISTORY);
        if (rule == null || rule.getMaxCount() == null) {
            listener.onResponse(false);
            return;
        }

        int maxCount = rule.getMaxCount();

        // Check if pinned count alone exceeds max_count (fire-and-forget warning)
        checkPinnedExceedsMaxCount(historyIndex, containerId, "history", maxCount);

        // Step 1: count non-pinned history docs
        SearchRequest countRequest = new SearchRequest(historyIndex);
        countRequest.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder countQuery = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true));

        SearchSourceBuilder countSource = new SearchSourceBuilder().query(countQuery).size(0).trackTotalHits(true);
        countRequest.source(countSource);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(countRequest, ActionListener.wrap(countResponse -> {
                long totalCount = countResponse.getHits().getTotalHits().value();
                if (totalCount <= maxCount) {
                    log.info("[MemoryRetentionJob] container={} history_deleted=0", containerId);
                    listener.onResponse(false);
                    return;
                }

                int excess = (int) (totalCount - maxCount);
                // Step 2: search oldest excess docs by created_time ASC (history uses created_time)
                collectOldestDocIds(historyIndex, containerId, CREATED_TIME_FIELD, excess, ActionListener.wrap(idsToDelete -> {
                    if (idsToDelete.isEmpty()) {
                        log.info("[MemoryRetentionJob] container={} history_deleted=0", containerId);
                        listener.onResponse(false);
                        return;
                    }
                    // Step 3: bulk delete those IDs
                    deleteDocumentsBatch(historyIndex, new ArrayList<>(idsToDelete), ActionListener.wrap(deleted -> {
                        log.info("[MemoryRetentionJob] container={} history_deleted={}", containerId, deleted);
                        listener.onResponse(deleted > 0);
                    }, listener::onFailure));
                }, listener::onFailure));
            }, listener::onFailure));
        }
    }

    private void executeWorkingMemoryTTL(MemoryConfiguration config, String containerId, ActionListener<Boolean> listener) {
        if (!config.isDisableSession()) {
            listener.onResponse(false);
            return;
        }

        String workingIndex = config.getIndexName(MemoryType.WORKING);
        if (workingIndex == null) {
            listener.onResponse(false);
            return;
        }

        int ttlDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_WORKING_MEMORY_TTL_DAYS);
        // TTL is off by default (-1): with no configured TTL, session-less working memory is kept indefinitely.
        // Only age it out when an operator sets a positive value.
        if (ttlDays <= 0) {
            listener.onResponse(false);
            return;
        }
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays);
        log
            .debug(
                "[MemoryRetentionJob] container={} working_memory TTL DBQ index={} cutoffMillis={}",
                containerId,
                workingIndex,
                cutoffMillis
            );

        DeleteByQueryRequest dbq = new DeleteByQueryRequest(workingIndex);
        dbq.setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        // Epoch-aware cutoff: legacy docs store created_time in epoch seconds and would all match
        // a plain lt(cutoffMillis) range. See normalizeTimestamp().
        dbq
            .setQuery(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
                    .must(buildEpochAwareCutoffQuery(CREATED_TIME_FIELD, cutoffMillis))
            );
        dbq.setRefresh(true);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.execute(DeleteByQueryAction.INSTANCE, dbq, ActionListener.wrap((BulkByScrollResponse response) -> {
                log.info("[MemoryRetentionJob] container={} working_memory_ttl_deleted={}", containerId, response.getDeleted());
                listener.onResponse(response.getDeleted() > 0);
            }, listener::onFailure));
        }
    }

    private void checkPinnedExceedsMaxCount(String index, String containerId, String memoryType, int maxCount) {
        SearchRequest pinnedCountRequest = new SearchRequest(index);
        pinnedCountRequest.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder pinnedQuery = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .must(QueryBuilders.termQuery(PINNED_FIELD, true));

        SearchSourceBuilder pinnedSource = new SearchSourceBuilder().query(pinnedQuery).size(0).trackTotalHits(true);
        pinnedCountRequest.source(pinnedSource);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(pinnedCountRequest, ActionListener.wrap(pinnedResponse -> {
                long pinnedCount = pinnedResponse.getHits().getTotalHits().value();
                if (pinnedCount > maxCount) {
                    log
                        .warn(
                            "[MemoryRetentionJob] container={} type={} pinned_count={} exceeds max_count={}."
                                + " Container will grow unbounded until pins are removed.",
                            containerId,
                            memoryType,
                            pinnedCount,
                            maxCount
                        );
                }
            }, e -> { log.debug("Failed to check pinned count for container [{}] type [{}]", containerId, memoryType, e); }));
        }
    }

    private void executeOrphanSweep() {
        resolveOrphanSweepContainers(null);
    }

    private void resolveOrphanSweepContainers(Object[] searchAfterValues) {
        SearchRequest request = new SearchRequest(ML_MEMORY_CONTAINER_INDEX);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.existsQuery(MEMORY_STORAGE_CONFIG_FIELD + "." + RETENTION_POLICY_FIELD))
            .mustNot(QueryBuilders.termQuery(MEMORY_STORAGE_CONFIG_FIELD + "." + DISABLE_SESSION_FIELD, true));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(query)
            .size(CONTAINER_PAGE_SIZE)
            .sort("_id", SortOrder.ASC)
            .fetchSource(true);

        if (searchAfterValues != null) {
            sourceBuilder.searchAfter(searchAfterValues);
        }

        request.source(sourceBuilder);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(request, ActionListener.wrap(response -> {
                SearchHits hits = response.getHits();
                SearchHit[] hitArray = hits.getHits();

                if (hitArray.length == 0) {
                    onJobComplete();
                    return;
                }

                Object[] nextPageSort = hitArray.length == CONTAINER_PAGE_SIZE ? hitArray[hitArray.length - 1].getSortValues() : null;
                processOrphanSweepContainerChain(hitArray, 0, nextPageSort);
            }, e -> {
                log.error("Failed to search containers for orphan sweep", e);
                onJobComplete();
            }));
        }
    }

    private void processOrphanSweepContainerChain(SearchHit[] hits, int index, Object[] nextPageSortValues) {
        if (index >= hits.length) {
            if (nextPageSortValues != null) {
                resolveOrphanSweepContainers(nextPageSortValues);
            } else {
                onJobComplete();
            }
            return;
        }

        SearchHit hit = hits[index];
        String containerId = hit.getId();

        try {
            Map<String, Object> source = hit.getSourceAsMap();
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) source.get(MEMORY_STORAGE_CONFIG_FIELD);

            if (configMap == null) {
                processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues);
                return;
            }

            XContentBuilder xContentBuilder = XContentFactory.jsonBuilder().map(configMap);
            BytesReference bytes = BytesReference.bytes(xContentBuilder);
            MemoryConfiguration config;
            try (
                XContentParser parser = XContentHelper
                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, bytes, XContentType.JSON)
            ) {
                parser.nextToken();
                config = MemoryConfiguration.parse(parser);
            }

            String workingIndex = config.getIndexName(MemoryType.WORKING);
            String sessionsIndex = config.getIndexName(MemoryType.SESSIONS);

            if (workingIndex == null || sessionsIndex == null) {
                processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues);
                return;
            }

            // Safety check: if the working memory index doesn't exist there is nothing to sweep,
            // and if the sessions index doesn't exist the sweep would classify ALL working memory
            // as orphaned and delete it. Skip the sweep in either case.
            if (!clusterService.state().metadata().hasIndex(workingIndex)) {
                log
                    .debug(
                        "[MemoryRetentionJob] Skipping orphan sweep for container={}: working memory index {} does not exist",
                        containerId,
                        workingIndex
                    );
                processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues);
                return;
            }

            if (!clusterService.state().metadata().hasIndex(sessionsIndex)) {
                log
                    .debug(
                        "[MemoryRetentionJob] Skipping orphan sweep for container={}: sessions index {} does not exist",
                        containerId,
                        sessionsIndex
                    );
                processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues);
                return;
            }

            // First-observation grace: defer all orphan deletion until orphan_ttl_days after the sweep first
            // observed this container. Orphan deletion keys off created_time, so without this gate a container
            // that already holds working memory older than orphan_ttl_days (e.g. written before this feature
            // shipped, or via paths that create no session doc) would have that memory deleted on the very
            // first sweep. Stamping a baseline and waiting one full window gives such memory a chance to be
            // backed by a session doc (create-session, or the lazy upsert on the add path) before it can be
            // classified as orphaned. Deletion is only delayed by one window, never skipped.
            Long baseline = extractTimestamp(hit, ORPHAN_SWEEP_BASELINE_TIME_FIELD);
            long now = System.currentTimeMillis();
            if (baseline == null) {
                log
                    .info(
                        "[MemoryRetentionJob] container={} first orphan-sweep observation; stamping baseline and deferring "
                            + "orphan deletion for one orphan_ttl_days window",
                        containerId
                    );
                stampOrphanSweepBaseline(containerId, now);
                processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues);
                return;
            }

            int orphanTtlDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_ORPHAN_TTL_DAYS);
            long graceUntil = baseline + TimeUnit.DAYS.toMillis(orphanTtlDays);
            if (now < graceUntil) {
                log
                    .debug(
                        "[MemoryRetentionJob] container={} still within orphan-sweep grace window (baseline={}, grace_until={}); "
                            + "skipping orphan deletion this run",
                        containerId,
                        baseline,
                        graceUntil
                    );
                processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues);
                return;
            }

            sweepOrphansForContainer(
                config,
                containerId,
                workingIndex,
                sessionsIndex,
                ActionListener.wrap(v -> processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues), e -> {
                    log.error("[MemoryRetentionJob] Orphan sweep failed for container [{}]", containerId, e);
                    processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues);
                })
            );
        } catch (Exception e) {
            log.error("[MemoryRetentionJob] Error parsing config for orphan sweep container [{}]", containerId, e);
            processOrphanSweepContainerChain(hits, index + 1, nextPageSortValues);
        }
    }

    /**
     * Records the epoch-millis baseline at which the orphan sweep first observed a container. Uses a conditional
     * painless script so the baseline is written exactly once: if it already exists the update is a noop, so a
     * concurrent or later run never resets the grace window. Best-effort — a failure just means the baseline is
     * retried on the next run, which only ever delays (never advances) the first deletion, so it is safe.
     */
    private void stampOrphanSweepBaseline(String containerId, long baselineMillis) {
        try {
            String scriptSource = "if (ctx._source.containsKey(params.field)) { ctx.op = 'noop'; }"
                + " else { ctx._source.put(params.field, params.baseline); }";
            Map<String, Object> scriptParams = Map.of("field", ORPHAN_SWEEP_BASELINE_TIME_FIELD, "baseline", baselineMillis);
            UpdateRequest updateRequest = new UpdateRequest(ML_MEMORY_CONTAINER_INDEX, containerId)
                .script(new Script(ScriptType.INLINE, "painless", scriptSource, scriptParams))
                .retryOnConflict(3);
            try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
                client
                    .update(
                        updateRequest,
                        ActionListener
                            .wrap(
                                response -> log.debug("[MemoryRetentionJob] container={} orphan-sweep baseline recorded", containerId),
                                e -> {
                                    if (ExceptionsHelper.unwrapCause(e) instanceof VersionConflictEngineException) {
                                        log.debug("[MemoryRetentionJob] container={} orphan-sweep baseline already set", containerId);
                                    } else {
                                        log
                                            .warn(
                                                "[MemoryRetentionJob] Failed to record orphan-sweep baseline for container [{}]; "
                                                    + "will retry next run",
                                                containerId,
                                                e
                                            );
                                    }
                                }
                            )
                    );
            }
        } catch (Exception e) {
            log.warn("[MemoryRetentionJob] Failed to build orphan-sweep baseline update for container [{}]", containerId, e);
        }
    }

    private void sweepOrphansForContainer(
        MemoryConfiguration config,
        String containerId,
        String workingIndex,
        String sessionsIndex,
        ActionListener<Void> listener
    ) {
        Set<String> sessionIds = new HashSet<>();
        String startKey = randomEnumerationStartKey();
        enumerateSessionIdsFromWorkingMemory(
            workingIndex,
            containerId,
            sessionIds,
            Map.of(SESSION_ID_FIELD, startKey),
            startKey,
            false,
            ActionListener
                .wrap(
                    aggregatedIds -> enumerateLegacySessionIdsFromWorkingMemory(
                        workingIndex,
                        containerId,
                        aggregatedIds,
                        new Object[] { startKey },
                        startKey,
                        false,
                        ActionListener.wrap(collectedIds -> {
                            ActionListener<Long> afterOrphans = ActionListener.wrap(orphansDeleted -> {
                                deleteEmptyNamespaceWorkingMemory(containerId, workingIndex, ActionListener.wrap(nullDeleted -> {
                                    log
                                        .info(
                                            "[MemoryRetentionJob] container={} orphans_deleted={} empty_namespace_deleted={}",
                                            containerId,
                                            orphansDeleted,
                                            nullDeleted
                                        );
                                    listener.onResponse(null);
                                }, listener::onFailure));
                            }, listener::onFailure);

                            if (collectedIds.isEmpty()) {
                                afterOrphans.onResponse(0L);
                            } else {
                                checkAndDeleteOrphans(containerId, workingIndex, sessionsIndex, collectedIds, afterOrphans);
                            }
                        }, listener::onFailure)
                    ),
                    listener::onFailure
                )
        );
    }

    /**
     * Generates a random keyspace position to start orphan enumeration from. Enumeration begins at
     * this key, wraps around to the start of the keyspace once exhausted, and stops when it reaches
     * the start key again. A single run still achieves full coverage when under the cap, but when
     * ORPHAN_SESSION_ID_CAP truncates enumeration, each run truncates a different keyspace slice so
     * all regions are probabilistically covered across runs.
     */
    private String randomEnumerationStartKey() {
        Random random = new Random();
        StringBuilder key = new StringBuilder(ORPHAN_START_KEY_LENGTH);
        for (int i = 0; i < ORPHAN_START_KEY_LENGTH; i++) {
            key.append(ORPHAN_KEYSPACE_ALPHABET.charAt(random.nextInt(ORPHAN_KEYSPACE_ALPHABET.length())));
        }
        return key.toString();
    }

    /**
     * Enumerates distinct session IDs from working memory via a paged composite aggregation on the
     * top-level {@code session_id} keyword field. Cost is proportional to the number of unique
     * sessions rather than total documents. Pre-upgrade documents lack the top-level field (the
     * {@code namespace} field is flat_object and cannot be aggregated) and are picked up separately
     * by {@link #enumerateLegacySessionIdsFromWorkingMemory}. Enumeration starts at a per-run random
     * key and wraps around so the ORPHAN_SESSION_ID_CAP truncates a different slice each run.
     */
    private void enumerateSessionIdsFromWorkingMemory(
        String workingIndex,
        String containerId,
        Set<String> sessionIds,
        Map<String, Object> afterKey,
        String startKey,
        boolean wrapped,
        ActionListener<Set<String>> listener
    ) {
        SearchRequest request = new SearchRequest(workingIndex);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.existsQuery(SESSION_ID_FIELD))
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId));

        CompositeAggregationBuilder aggregation = new CompositeAggregationBuilder(
            SESSION_IDS_AGG_NAME,
            List.of(new TermsValuesSourceBuilder(SESSION_ID_FIELD).field(SESSION_ID_FIELD))
        ).size(ORPHAN_SESSION_BATCH_SIZE);

        if (afterKey != null) {
            aggregation.aggregateAfter(afterKey);
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query).size(0).aggregation(aggregation);
        request.source(sourceBuilder);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(request, ActionListener.wrap(response -> {
                CompositeAggregation composite = response.getAggregations() != null
                    ? response.getAggregations().get(SESSION_IDS_AGG_NAME)
                    : null;
                if (composite == null) {
                    listener.onResponse(sessionIds);
                    return;
                }

                for (CompositeAggregation.Bucket bucket : composite.getBuckets()) {
                    Object sessionId = bucket.getKey().get(SESSION_ID_FIELD);
                    if (sessionId != null) {
                        if (wrapped && sessionId.toString().compareTo(startKey) > 0) {
                            listener.onResponse(sessionIds);
                            return;
                        }
                        sessionIds.add(sessionId.toString());
                    }

                    if (sessionIds.size() >= ORPHAN_SESSION_ID_CAP) {
                        log
                            .warn(
                                "[MemoryRetentionJob] container={} session_id cap ({}) reached during orphan enumeration."
                                    + " Remaining orphans will be covered in future runs.",
                                containerId,
                                ORPHAN_SESSION_ID_CAP
                            );
                        listener.onResponse(sessionIds);
                        return;
                    }
                }

                Map<String, Object> nextAfterKey = composite.afterKey();
                if (composite.getBuckets().size() == ORPHAN_SESSION_BATCH_SIZE && nextAfterKey != null) {
                    enumerateSessionIdsFromWorkingMemory(workingIndex, containerId, sessionIds, nextAfterKey, startKey, wrapped, listener);
                } else if (!wrapped) {
                    enumerateSessionIdsFromWorkingMemory(workingIndex, containerId, sessionIds, null, startKey, true, listener);
                } else {
                    listener.onResponse(sessionIds);
                }
            }, listener::onFailure));
        }
    }

    /**
     * Backward-compatibility enumeration for pre-upgrade working-memory documents that were indexed
     * before the top-level {@code session_id} field existed. Scans only documents that have
     * {@code namespace.session_id} but lack the top-level field, so its cost shrinks to zero as
     * legacy documents are cleaned up. Like the aggregation path, scanning starts at the per-run
     * random key in {@code _id} order and wraps around so cap truncation covers a different slice
     * each run.
     */
    private void enumerateLegacySessionIdsFromWorkingMemory(
        String workingIndex,
        String containerId,
        Set<String> sessionIds,
        Object[] searchAfterValues,
        String startKey,
        boolean wrapped,
        ActionListener<Set<String>> listener
    ) {
        if (sessionIds.size() >= ORPHAN_SESSION_ID_CAP) {
            listener.onResponse(sessionIds);
            return;
        }

        SearchRequest request = new SearchRequest(workingIndex);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.existsQuery(NAMESPACE_FIELD + "." + SESSION_ID_FIELD))
            .mustNot(QueryBuilders.existsQuery(SESSION_ID_FIELD))
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(query)
            .size(ORPHAN_ENUMERATION_PAGE_SIZE)
            .sort("_id", SortOrder.ASC)
            .fetchSource(new String[] { NAMESPACE_FIELD }, null);

        if (searchAfterValues != null) {
            sourceBuilder.searchAfter(searchAfterValues);
        }

        request.source(sourceBuilder);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(request, ActionListener.wrap(response -> {
                SearchHit[] hits = response.getHits().getHits();

                for (SearchHit hit : hits) {
                    if (wrapped && hit.getId().compareTo(startKey) > 0) {
                        listener.onResponse(sessionIds);
                        return;
                    }

                    Map<String, Object> source = hit.getSourceAsMap();
                    if (source != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> namespace = (Map<String, Object>) source.get(NAMESPACE_FIELD);
                        if (namespace != null) {
                            String sessionId = (String) namespace.get(SESSION_ID_FIELD);
                            if (sessionId != null) {
                                sessionIds.add(sessionId);
                            }
                        }
                    }

                    if (sessionIds.size() >= ORPHAN_SESSION_ID_CAP) {
                        log
                            .warn(
                                "[MemoryRetentionJob] container={} session_id cap ({}) reached during orphan enumeration."
                                    + " Remaining orphans will be covered in future runs.",
                                containerId,
                                ORPHAN_SESSION_ID_CAP
                            );
                        listener.onResponse(sessionIds);
                        return;
                    }
                }

                if (hits.length == ORPHAN_ENUMERATION_PAGE_SIZE) {
                    Object[] nextSearchAfter = hits[hits.length - 1].getSortValues();
                    enumerateLegacySessionIdsFromWorkingMemory(
                        workingIndex,
                        containerId,
                        sessionIds,
                        nextSearchAfter,
                        startKey,
                        wrapped,
                        listener
                    );
                } else if (!wrapped) {
                    enumerateLegacySessionIdsFromWorkingMemory(workingIndex, containerId, sessionIds, null, startKey, true, listener);
                } else {
                    listener.onResponse(sessionIds);
                }
            }, listener::onFailure));
        }
    }

    private void checkAndDeleteOrphans(
        String containerId,
        String workingIndex,
        String sessionsIndex,
        Set<String> sessionIds,
        ActionListener<Long> listener
    ) {
        List<List<String>> batches = partition(new ArrayList<>(sessionIds), ORPHAN_SESSION_BATCH_SIZE);
        Set<String> allOrphanIds = new HashSet<>();
        checkOrphanBatch(containerId, sessionsIndex, batches, 0, allOrphanIds, ActionListener.wrap(orphanIds -> {
            if (orphanIds.isEmpty()) {
                listener.onResponse(0L);
                return;
            }
            deleteOrphanedWorkingMemory(containerId, workingIndex, orphanIds, listener);
        }, listener::onFailure));
    }

    private void checkOrphanBatch(
        String containerId,
        String sessionsIndex,
        List<List<String>> batches,
        int batchIndex,
        Set<String> allOrphanIds,
        ActionListener<Set<String>> listener
    ) {
        if (batchIndex >= batches.size()) {
            listener.onResponse(allOrphanIds);
            return;
        }

        List<String> batch = batches.get(batchIndex);

        SearchRequest request = new SearchRequest(sessionsIndex);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termsQuery("_id", batch))
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query).size(batch.size()).fetchSource(false);

        request.source(sourceBuilder);
        request.allowPartialSearchResults(false);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(request, ActionListener.wrap(response -> {
                if (response.getFailedShards() > 0 || response.isTimedOut()) {
                    listener
                        .onFailure(
                            new IllegalStateException(
                                "Orphan check for container ["
                                    + containerId
                                    + "] had "
                                    + response.getFailedShards()
                                    + " failed shards or timed out; skipping to avoid incorrect deletions"
                            )
                        );
                    return;
                }

                Set<String> existingIds = new HashSet<>();
                for (SearchHit hit : response.getHits().getHits()) {
                    existingIds.add(hit.getId());
                }

                for (String sessionId : batch) {
                    if (!existingIds.contains(sessionId)) {
                        allOrphanIds.add(sessionId);
                    }
                }

                checkOrphanBatch(containerId, sessionsIndex, batches, batchIndex + 1, allOrphanIds, listener);
            }, listener::onFailure));
        }
    }

    private void deleteOrphanedWorkingMemory(
        String containerId,
        String workingIndex,
        Set<String> orphanSessionIds,
        ActionListener<Long> listener
    ) {
        List<List<String>> batches = partition(new ArrayList<>(orphanSessionIds), DELETE_BY_QUERY_BATCH_SIZE);
        deleteOrphanBatch(containerId, workingIndex, batches, 0, 0L, listener);
    }

    private void deleteOrphanBatch(
        String containerId,
        String workingIndex,
        List<List<String>> batches,
        int batchIndex,
        long totalDeleted,
        ActionListener<Long> listener
    ) {
        if (batchIndex >= batches.size()) {
            listener.onResponse(totalDeleted);
            return;
        }

        List<String> batch = batches.get(batchIndex);
        if (log.isDebugEnabled()) {
            log.debug("[MemoryRetentionJob] container={} deleting orphaned working memory for session ids={}", containerId, batch);
        }

        int orphanTtlDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_ORPHAN_TTL_DAYS);
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(orphanTtlDays);

        DeleteByQueryRequest dbq = new DeleteByQueryRequest(workingIndex);
        dbq.setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        dbq
            .setQuery(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
                    .must(buildSessionIdMatchQuery(batch))
                    .must(buildEpochAwareCutoffQuery(CREATED_TIME_FIELD, cutoffMillis))
            );
        dbq.setRefresh(true);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.execute(DeleteByQueryAction.INSTANCE, dbq, ActionListener.wrap((BulkByScrollResponse bulkResponse) -> {
                long deleted = bulkResponse.getDeleted();
                deleteOrphanBatch(containerId, workingIndex, batches, batchIndex + 1, totalDeleted + deleted, listener);
            }, listener::onFailure));
        }
    }

    private void deleteEmptyNamespaceWorkingMemory(String containerId, String workingIndex, ActionListener<Long> listener) {
        int orphanTtlDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_ORPHAN_TTL_DAYS);
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(orphanTtlDays);
        log
            .debug(
                "[MemoryRetentionJob] container={} empty_namespace DBQ index={} cutoffMillis={}",
                containerId,
                workingIndex,
                cutoffMillis
            );

        DeleteByQueryRequest dbq = new DeleteByQueryRequest(workingIndex);
        dbq.setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        // Epoch-aware cutoff: legacy docs store created_time in epoch seconds and would all match
        // a plain lt(cutoffMillis) range. See normalizeTimestamp().
        dbq
            .setQuery(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
                    .mustNot(QueryBuilders.existsQuery(SESSION_ID_FIELD))
                    .mustNot(QueryBuilders.existsQuery(NAMESPACE_FIELD + "." + SESSION_ID_FIELD))
                    .mustNot(QueryBuilders.existsQuery(NAMESPACE_FIELD + "." + USER_ID_FIELD))
                    .mustNot(QueryBuilders.existsQuery(NAMESPACE_FIELD + "." + AGENT_ID_FIELD))
                    .must(buildEpochAwareCutoffQuery(CREATED_TIME_FIELD, cutoffMillis))
            );
        dbq.setRefresh(true);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.execute(DeleteByQueryAction.INSTANCE, dbq, ActionListener.wrap((BulkByScrollResponse response) -> {
                listener.onResponse(response.getDeleted());
            }, listener::onFailure));
        }
    }

    // =====================================================================================================
    // DRY-RUN: preview exactly what the scheduled retention job WOULD delete, performing zero deletions.
    //
    // These methods reuse the same read-only identify/count/enumerate logic the real job uses to decide
    // what to evict (identifyTimeBasedExpiredSessions, identifyCountBasedExpiredSessions, checkOrphanBatch,
    // enumerate*SessionIds*, buildEpochAwareCutoffQuery, buildSessionIdMatchQuery, computeDefaultRetentionPolicy).
    // No DeleteRequest / DeleteByQuery / bulk delete is ever issued from this path. Because the counting reuses
    // the job's own predicates, the reported numbers track the job's behavior and cannot silently drift.
    //
    // Per-pass attribution mirrors each pass's real algorithm:
    // - sessions: the job computes the UNION of the time-expired and count-expired id sets, then deletes it
    // in one pass. We reuse both identify methods and attribute retention_days = |timeSet|,
    // max_count = |countSet \ timeSet| (disjoint, sums to |union|).
    // - long_term: the job deletes time-expired docs FIRST, then evicts by count on the REMAINDER. We compute
    // the count-based excess against the post-time-deletion population analytically so time and
    // count reasons stay disjoint, matching what the next run would delete.
    // - history: count-only (retention_days is rejected by validation for history), so retention_days = 0.
    // - working: cascade (sessions that expire), ttl (session-less containers only), and orphan sweep.
    // =====================================================================================================

    /**
     * The effective retention policy for a dry-run: the policy map that would actually be evaluated plus the
     * {@code MemoryRetentionDryRunResult.POLICY_SOURCE_*} constant describing where it came from. The dry-run is
     * read-only, so this is computed locally and never written back onto the caller-owned {@link MemoryConfiguration}.
     */
    static final class EffectivePolicy {
        final String source;
        final Map<MemoryType, RetentionRule> policy;

        EffectivePolicy(String source, Map<MemoryType, RetentionRule> policy) {
            this.source = source;
            this.policy = policy;
        }
    }

    /**
     * Resolves the effective retention policy for a dry-run and reports its source. Does NOT mutate {@code config}:
     * for the "default" case it backfills cluster defaults into a LOCAL map (exactly the policy the real job would
     * persist before evaluating) and returns it, leaving the caller's configuration untouched. The returned
     * {@link EffectivePolicy#source} is one of the {@code MemoryRetentionDryRunResult.POLICY_SOURCE_*} constants.
     */
    @VisibleForTesting
    EffectivePolicy resolveEffectivePolicyForDryRun(MemoryConfiguration config) {
        Map<MemoryType, RetentionRule> stored = config.getRetentionPolicy();
        if (stored != null && !stored.isEmpty()) {
            return new EffectivePolicy(MemoryRetentionDryRunResult.POLICY_SOURCE_STORED, stored);
        }
        if (config.isRetentionPolicyExplicitlyNull()) {
            return new EffectivePolicy(MemoryRetentionDryRunResult.POLICY_SOURCE_NONE, stored);
        }
        Map<MemoryType, RetentionRule> defaults = computeDefaultRetentionPolicy();
        if (defaults == null) {
            return new EffectivePolicy(MemoryRetentionDryRunResult.POLICY_SOURCE_NONE, stored);
        }
        return new EffectivePolicy(MemoryRetentionDryRunResult.POLICY_SOURCE_DEFAULT, defaults);
    }

    /** Mutable accumulator threaded through the dry-run pass chain. */
    private static final class DryRunContext {
        final String containerId;
        final String policySource;
        final long evaluatedAt;
        final Map<MemoryType, RetentionRule> effectivePolicy;
        final Integer workingMemoryTtlDays;
        final List<String> warnings = new ArrayList<>();

        MemoryRetentionDryRunResult.TypeDeletion sessions = zero();
        MemoryRetentionDryRunResult.TypeDeletion longTerm = zero();
        MemoryRetentionDryRunResult.TypeDeletion history = zero();
        long cascade = 0;
        long ttl = 0;
        long orphan = 0;
        long pinnedWouldSkip = 0;
        Set<String> expiredSessionUnion = new HashSet<>();

        DryRunContext(
            String containerId,
            String policySource,
            long evaluatedAt,
            Map<MemoryType, RetentionRule> effectivePolicy,
            Integer workingMemoryTtlDays
        ) {
            this.containerId = containerId;
            this.policySource = policySource;
            this.evaluatedAt = evaluatedAt;
            this.effectivePolicy = effectivePolicy;
            this.workingMemoryTtlDays = workingMemoryTtlDays;
        }

        static MemoryRetentionDryRunResult.TypeDeletion zero() {
            return new MemoryRetentionDryRunResult.TypeDeletion(0, new LinkedHashMap<>());
        }
    }

    /**
     * Entry point for a single-container dry-run. Resolves the effective policy, then evaluates every retention
     * pass read-only and assembles a {@link MemoryRetentionDryRunResult}. {@code orphanBaselineMillis} is the
     * container's stored orphan-sweep baseline (or {@code null} if never stamped), used to honor the same
     * first-observation grace window the real orphan sweep applies.
     */
    public void dryRunContainer(
        MemoryConfiguration config,
        String containerId,
        Long orphanBaselineMillis,
        ActionListener<MemoryRetentionDryRunResult> listener
    ) {
        try {
            EffectivePolicy effective = resolveEffectivePolicyForDryRun(config);
            String policySource = effective.source;
            int ttlDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_WORKING_MEMORY_TTL_DAYS);
            DryRunContext ctx = new DryRunContext(containerId, policySource, System.currentTimeMillis(), effective.policy, ttlDays);

            // Multi-tenancy hard-gates the scheduled job off - run() returns before any deletion when MT is
            // enabled - so nothing would ever be deleted in this deployment mode. Skip the read-only passes and
            // report an honest zero rather than scanning indices for counts the job can't act on.
            // NOTE(PR-B): retention runs under multi-tenancy in PR-B; this becomes an isRemoteMetadataStore()
            // gate there, but the skip-to-zero behavior is unchanged.
            if (clusterService.getClusterSettings().get(ML_COMMONS_MULTI_TENANCY_ENABLED)) {
                ctx.warnings.add("multi-tenancy is enabled; the scheduled retention job does not run, so nothing would be deleted");
                listener.onResponse(buildDryRunResult(ctx));
                return;
            }
            // Retention is disabled cluster-wide, so run() would not execute and nothing would be deleted. Skip the
            // read-only passes and report an honest zero - the whole feature stays gated behind this flag.
            if (!clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_RETENTION_ENABLED)) {
                ctx.warnings
                    .add(
                        "retention is disabled cluster-wide (plugins.ml_commons.memory.retention_enabled=false);"
                            + " the scheduled job will not run, so nothing would be deleted"
                    );
                listener.onResponse(buildDryRunResult(ctx));
                return;
            }
            if (MemoryRetentionDryRunResult.POLICY_SOURCE_NONE.equals(policySource)) {
                ctx.warnings.add("container has no retention policy and no cluster defaults apply; nothing would be deleted");
            }

            // sessions -> long-term -> history -> working -> finalize
            dryRunSessions(
                config,
                ctx,
                ActionListener
                    .wrap(
                        v1 -> dryRunLongTerm(
                            config,
                            ctx,
                            ActionListener
                                .wrap(
                                    v2 -> dryRunHistory(
                                        config,
                                        ctx,
                                        ActionListener
                                            .wrap(v3 -> dryRunWorking(config, ctx, orphanBaselineMillis, ActionListener.wrap(v4 -> {
                                                listener.onResponse(buildDryRunResult(ctx));
                                            }, listener::onFailure)), listener::onFailure)
                                    ),
                                    listener::onFailure
                                )
                        ),
                        listener::onFailure
                    )
            );
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private MemoryRetentionDryRunResult buildDryRunResult(DryRunContext ctx) {
        LinkedHashMap<String, Long> workingByReason = new LinkedHashMap<>();
        workingByReason.put(MemoryRetentionDryRunResult.REASON_CASCADE, ctx.cascade);
        workingByReason.put(MemoryRetentionDryRunResult.REASON_TTL, ctx.ttl);
        workingByReason.put(MemoryRetentionDryRunResult.REASON_ORPHAN, ctx.orphan);
        long workingTotal = ctx.cascade + ctx.ttl + ctx.orphan;
        MemoryRetentionDryRunResult.TypeDeletion workingMemory = new MemoryRetentionDryRunResult.TypeDeletion(
            workingTotal,
            workingByReason
        );

        long total = ctx.sessions.getTotal() + ctx.longTerm.getTotal() + ctx.history.getTotal() + workingTotal;

        return MemoryRetentionDryRunResult
            .builder()
            .containerId(ctx.containerId)
            .evaluatedAt(ctx.evaluatedAt)
            .policySource(ctx.policySource)
            .effectivePolicy(ctx.effectivePolicy)
            .workingMemoryTtlDays(ctx.workingMemoryTtlDays)
            .sessions(ctx.sessions)
            .longTerm(ctx.longTerm)
            .history(ctx.history)
            .workingMemory(workingMemory)
            .totalWouldDelete(total)
            .pinnedWouldSkip(ctx.pinnedWouldSkip)
            .warnings(ctx.warnings)
            .build();
    }

    private void dryRunSessions(MemoryConfiguration config, DryRunContext ctx, ActionListener<Void> listener) {
        String sessionIndex = config.getIndexName(MemoryType.SESSIONS);
        Map<MemoryType, RetentionRule> policy = ctx.effectivePolicy;
        RetentionRule rule = policy == null ? null : policy.get(MemoryType.SESSIONS);
        if (sessionIndex == null || rule == null) {
            listener.onResponse(null);
            return;
        }

        Set<String> timeSet = new HashSet<>();
        ActionListener<Set<String>> afterCount = ActionListener.wrap(countSet -> {
            // Attribute reasons disjointly: retention_days owns the time set; max_count owns only the
            // count-expired ids NOT already covered by time expiry. Sum equals |union| (what the job deletes).
            long timeCount = timeSet.size();
            long countOnly = countSet.stream().filter(id -> !timeSet.contains(id)).count();
            long unionTotal = timeCount + countOnly;

            ctx.expiredSessionUnion = new HashSet<>(timeSet);
            ctx.expiredSessionUnion.addAll(countSet);

            LinkedHashMap<String, Long> byReason = new LinkedHashMap<>();
            byReason.put(MemoryRetentionDryRunResult.REASON_RETENTION_DAYS, timeCount);
            byReason.put(MemoryRetentionDryRunResult.REASON_MAX_COUNT, countOnly);
            ctx.sessions = new MemoryRetentionDryRunResult.TypeDeletion(unionTotal, byReason);

            if (rule.getMaxCount() != null && countSet.size() >= COUNT_BASED_ID_CAP) {
                addCapWarning(ctx, "sessions");
            }

            // pinned_would_skip: pinned sessions that fall inside the time-expiry window (would be deleted but
            // for the pin). Count-based eviction never considers pinned docs, so only the time window contributes.
            addPinnedWarningsAndSkip(sessionIndex, ctx, "sessions", rule, LAST_UPDATED_TIME_FIELD, listener);
        }, listener::onFailure);

        ActionListener<Set<String>> afterTime = ActionListener.wrap(t -> {
            timeSet.addAll(t);
            if (rule.getMaxCount() != null) {
                identifyCountBasedExpiredSessions(config, ctx.containerId, sessionIndex, rule.getMaxCount(), afterCount);
            } else {
                afterCount.onResponse(new HashSet<>());
            }
        }, listener::onFailure);

        if (rule.getRetentionDays() != null) {
            identifyTimeBasedExpiredSessions(config, ctx.containerId, sessionIndex, rule.getRetentionDays(), afterTime);
        } else {
            afterTime.onResponse(new HashSet<>());
        }
    }

    private void dryRunLongTerm(MemoryConfiguration config, DryRunContext ctx, ActionListener<Void> listener) {
        String longTermIndex = config.getIndexName(MemoryType.LONG_TERM);
        Map<MemoryType, RetentionRule> policy = ctx.effectivePolicy;
        RetentionRule rule = policy == null ? null : policy.get(MemoryType.LONG_TERM);
        if (longTermIndex == null || rule == null) {
            listener.onResponse(null);
            return;
        }

        ActionListener<Long> afterTime = ActionListener.wrap(timeCount -> {
            if (rule.getMaxCount() == null) {
                setSequentialTypeDeletion(ctx, MemoryType.LONG_TERM, timeCount, 0L);
                addPinnedWarningsAndSkip(longTermIndex, ctx, "long_term", rule, LAST_UPDATED_TIME_FIELD, listener);
                return;
            }
            int maxCount = rule.getMaxCount();
            // Count-based eviction runs on the population that REMAINS after time-based deletion.
            countNonPinned(longTermIndex, ctx.containerId, ActionListener.wrap(totalNonPinned -> {
                long remaining = Math.max(0, totalNonPinned - timeCount);
                long rawExcess = Math.max(0, remaining - maxCount);
                long cappedExcess = Math.min(rawExcess, COUNT_BASED_ID_CAP);
                if (rawExcess > COUNT_BASED_ID_CAP) {
                    addCapWarning(ctx, "long_term");
                }
                setSequentialTypeDeletion(ctx, MemoryType.LONG_TERM, timeCount, cappedExcess);
                addPinnedWarningsAndSkip(longTermIndex, ctx, "long_term", rule, LAST_UPDATED_TIME_FIELD, listener);
            }, listener::onFailure));
        }, listener::onFailure);

        if (rule.getRetentionDays() != null) {
            long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(rule.getRetentionDays());
            BoolQueryBuilder query = QueryBuilders
                .boolQuery()
                .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, ctx.containerId))
                .must(buildEpochAwareCutoffQuery(LAST_UPDATED_TIME_FIELD, cutoffMillis))
                .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true));
            countMatching(longTermIndex, query, afterTime);
        } else {
            afterTime.onResponse(0L);
        }
    }

    private void dryRunHistory(MemoryConfiguration config, DryRunContext ctx, ActionListener<Void> listener) {
        String historyIndex = config.getIndexName(MemoryType.HISTORY);
        Map<MemoryType, RetentionRule> policy = ctx.effectivePolicy;
        RetentionRule rule = policy == null ? null : policy.get(MemoryType.HISTORY);
        // History supports max_count only; retention_days is rejected by validation.
        if (historyIndex == null || rule == null || rule.getMaxCount() == null) {
            listener.onResponse(null);
            return;
        }
        int maxCount = rule.getMaxCount();
        countNonPinned(historyIndex, ctx.containerId, ActionListener.wrap(totalNonPinned -> {
            long rawExcess = Math.max(0, totalNonPinned - maxCount);
            long cappedExcess = Math.min(rawExcess, COUNT_BASED_ID_CAP);
            if (rawExcess > COUNT_BASED_ID_CAP) {
                addCapWarning(ctx, "history");
            }
            LinkedHashMap<String, Long> byReason = new LinkedHashMap<>();
            byReason.put(MemoryRetentionDryRunResult.REASON_RETENTION_DAYS, 0L);
            byReason.put(MemoryRetentionDryRunResult.REASON_MAX_COUNT, cappedExcess);
            ctx.history = new MemoryRetentionDryRunResult.TypeDeletion(cappedExcess, byReason);
            // History count-based eviction excludes pinned; add the pinned>max_count warning (no time window,
            // so no pinned_would_skip contribution).
            checkPinnedExceedsMaxCountForDryRun(
                historyIndex,
                ctx,
                "history",
                maxCount,
                ActionListener.wrap(v -> listener.onResponse(null), listener::onFailure)
            );
        }, listener::onFailure));
    }

    private void dryRunWorking(MemoryConfiguration config, DryRunContext ctx, Long orphanBaselineMillis, ActionListener<Void> listener) {
        String workingIndex = config.getIndexName(MemoryType.WORKING);
        if (workingIndex == null) {
            listener.onResponse(null);
            return;
        }

        // cascade: working memory belonging to sessions that would expire this run.
        ActionListener<Void> afterCascade = ActionListener.wrap(v -> {
            // ttl: only session-less (disable_session) containers age out working memory by TTL.
            dryRunWorkingTtl(config, ctx, workingIndex, ActionListener.wrap(v2 -> {
                // orphan: only runs for session-backed containers (mirrors resolveOrphanSweepContainers).
                dryRunOrphanSweep(config, ctx, workingIndex, orphanBaselineMillis, listener);
            }, listener::onFailure));
        }, listener::onFailure);

        if (ctx.expiredSessionUnion.isEmpty()) {
            afterCascade.onResponse(null);
        } else {
            countWorkingForSessions(
                workingIndex,
                ctx.containerId,
                new ArrayList<>(ctx.expiredSessionUnion),
                ActionListener.wrap(cascadeCount -> {
                    ctx.cascade = cascadeCount;
                    afterCascade.onResponse(null);
                }, listener::onFailure)
            );
        }
    }

    private void dryRunWorkingTtl(MemoryConfiguration config, DryRunContext ctx, String workingIndex, ActionListener<Void> listener) {
        if (!config.isDisableSession()) {
            listener.onResponse(null);
            return;
        }
        int ttlDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_WORKING_MEMORY_TTL_DAYS);
        if (ttlDays <= 0) {
            listener.onResponse(null);
            return;
        }
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays);
        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, ctx.containerId))
            .must(buildEpochAwareCutoffQuery(CREATED_TIME_FIELD, cutoffMillis));
        countMatching(workingIndex, query, ActionListener.wrap(c -> {
            ctx.ttl = c;
            listener.onResponse(null);
        }, listener::onFailure));
    }

    private void dryRunOrphanSweep(
        MemoryConfiguration config,
        DryRunContext ctx,
        String workingIndex,
        Long orphanBaselineMillis,
        ActionListener<Void> listener
    ) {
        String sessionsIndex = config.getIndexName(MemoryType.SESSIONS);
        Map<MemoryType, RetentionRule> policy = ctx.effectivePolicy;
        boolean hasPolicy = policy != null && !policy.isEmpty();
        // Orphan sweep only runs for session-backed containers that have a retention policy, and only after the
        // sweep's first-observation grace window has elapsed (mirrors resolveOrphanSweepContainers + baseline gate).
        if (!hasPolicy || config.isDisableSession() || sessionsIndex == null) {
            listener.onResponse(null);
            return;
        }
        // Mirror the real sweep's index-existence safety check (processOrphanSweepContainerChain): if the working
        // index doesn't exist there is nothing to sweep, and if the sessions index doesn't exist the sweep would
        // classify ALL working memory as orphaned. Skip (report 0) in either case so the dry-run matches the job.
        if (!clusterService.state().metadata().hasIndex(workingIndex) || !clusterService.state().metadata().hasIndex(sessionsIndex)) {
            listener.onResponse(null);
            return;
        }
        int orphanTtlDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_ORPHAN_TTL_DAYS);
        long now = System.currentTimeMillis();
        if (orphanBaselineMillis == null) {
            ctx.warnings
                .add(
                    "orphan sweep has not yet observed this container (no baseline); orphaned working memory would be"
                        + " deferred one orphan_ttl_days window before any deletion"
                );
            listener.onResponse(null);
            return;
        }
        long normalizedBaseline = normalizeTimestamp(orphanBaselineMillis);
        long graceUntil = normalizedBaseline + TimeUnit.DAYS.toMillis(orphanTtlDays);
        if (now < graceUntil) {
            ctx.warnings
                .add(
                    "orphan sweep is within its first-observation grace window for this container;"
                        + " orphaned working memory would not be deleted until the window elapses"
                );
            listener.onResponse(null);
            return;
        }

        long orphanCutoffMillis = now - TimeUnit.DAYS.toMillis(orphanTtlDays);

        // Enumerate session ids referenced by working memory (top-level field + legacy namespace subpath),
        // reusing the exact enumeration the real sweep uses.
        Set<String> sessionIds = new HashSet<>();
        String startKey = randomEnumerationStartKey();
        enumerateSessionIdsFromWorkingMemory(
            workingIndex,
            ctx.containerId,
            sessionIds,
            Map.of(SESSION_ID_FIELD, startKey),
            startKey,
            false,
            ActionListener
                .wrap(
                    aggIds -> enumerateLegacySessionIdsFromWorkingMemory(
                        workingIndex,
                        ctx.containerId,
                        aggIds,
                        new Object[] { startKey },
                        startKey,
                        false,
                        ActionListener.wrap(collectedIds -> {
                            ActionListener<Long> afterOrphanDocs = ActionListener.wrap(orphanDocs -> {
                                // Plus session-less/empty-namespace working memory older than the cutoff.
                                countEmptyNamespaceWorking(
                                    ctx.containerId,
                                    workingIndex,
                                    orphanCutoffMillis,
                                    ActionListener.wrap(emptyDocs -> {
                                        ctx.orphan = orphanDocs + emptyDocs;
                                        listener.onResponse(null);
                                    }, listener::onFailure)
                                );
                            }, listener::onFailure);

                            if (collectedIds.isEmpty()) {
                                afterOrphanDocs.onResponse(0L);
                            } else {
                                List<List<String>> batches = partition(new ArrayList<>(collectedIds), ORPHAN_SESSION_BATCH_SIZE);
                                Set<String> allOrphanIds = new HashSet<>();
                                checkOrphanBatch(
                                    ctx.containerId,
                                    sessionsIndex,
                                    batches,
                                    0,
                                    allOrphanIds,
                                    ActionListener.wrap(orphanIds -> {
                                        if (orphanIds.isEmpty()) {
                                            afterOrphanDocs.onResponse(0L);
                                        } else {
                                            countWorkingForOrphanSessions(
                                                ctx.containerId,
                                                workingIndex,
                                                new ArrayList<>(orphanIds),
                                                orphanCutoffMillis,
                                                afterOrphanDocs
                                            );
                                        }
                                    }, listener::onFailure)
                                );
                            }
                        }, listener::onFailure)
                    ),
                    listener::onFailure
                )
        );
    }

    // ---- dry-run count helpers (read-only) ----

    private void setSequentialTypeDeletion(DryRunContext ctx, MemoryType type, long timeCount, long countExcess) {
        LinkedHashMap<String, Long> byReason = new LinkedHashMap<>();
        byReason.put(MemoryRetentionDryRunResult.REASON_RETENTION_DAYS, timeCount);
        byReason.put(MemoryRetentionDryRunResult.REASON_MAX_COUNT, countExcess);
        MemoryRetentionDryRunResult.TypeDeletion td = new MemoryRetentionDryRunResult.TypeDeletion(timeCount + countExcess, byReason);
        if (type == MemoryType.LONG_TERM) {
            ctx.longTerm = td;
        } else if (type == MemoryType.HISTORY) {
            ctx.history = td;
        } else {
            ctx.sessions = td;
        }
    }

    private void addCapWarning(DryRunContext ctx, String type) {
        ctx.warnings
            .add(
                "count-based eviction for "
                    + type
                    + " exceeds the per-run cap of "
                    + COUNT_BASED_ID_CAP
                    + "; the dry-run reports the single-run amount and the remainder would be removed over subsequent runs"
            );
    }

    /**
     * Adds the "pinned exceeds max_count" warning (when a max_count rule is set) and accumulates
     * pinned_would_skip (pinned docs inside the time-expiry window, when a retention_days rule is set), then
     * signals completion. Two independent read-only counts; both are best-effort in the sense that failures
     * propagate via the listener.
     */
    private void addPinnedWarningsAndSkip(
        String index,
        DryRunContext ctx,
        String type,
        RetentionRule rule,
        String timeField,
        ActionListener<Void> listener
    ) {
        ActionListener<Void> afterPinnedExceed = ActionListener.wrap(v -> {
            if (rule.getRetentionDays() != null) {
                long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(rule.getRetentionDays());
                BoolQueryBuilder query = QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, ctx.containerId))
                    .must(QueryBuilders.termQuery(PINNED_FIELD, true))
                    .must(buildEpochAwareCutoffQuery(timeField, cutoffMillis));
                countMatching(index, query, ActionListener.wrap(pinnedExpired -> {
                    ctx.pinnedWouldSkip += pinnedExpired;
                    listener.onResponse(null);
                }, listener::onFailure));
            } else {
                listener.onResponse(null);
            }
        }, listener::onFailure);

        if (rule.getMaxCount() != null) {
            checkPinnedExceedsMaxCountForDryRun(index, ctx, type, rule.getMaxCount(), afterPinnedExceed);
        } else {
            afterPinnedExceed.onResponse(null);
        }
    }

    /**
     * Read-only mirror of {@link #checkPinnedExceedsMaxCount}: adds a warning when pinned docs alone exceed
     * max_count (the container will grow unbounded), then signals completion.
     */
    private void checkPinnedExceedsMaxCountForDryRun(
        String index,
        DryRunContext ctx,
        String type,
        int maxCount,
        ActionListener<Void> listener
    ) {
        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, ctx.containerId))
            .must(QueryBuilders.termQuery(PINNED_FIELD, true));
        countMatching(index, query, ActionListener.wrap(pinnedCount -> {
            if (pinnedCount > maxCount) {
                ctx.warnings
                    .add(
                        "pinned count ("
                            + pinnedCount
                            + ") exceeds max_count ("
                            + maxCount
                            + ") for "
                            + type
                            + " — container will grow unbounded"
                    );
            }
            listener.onResponse(null);
        }, listener::onFailure));
    }

    private void countNonPinned(String index, String containerId, ActionListener<Long> listener) {
        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true));
        countMatching(index, query, listener);
    }

    private void countWorkingForSessions(String workingIndex, String containerId, List<String> sessionIds, ActionListener<Long> listener) {
        List<List<String>> batches = partition(sessionIds, DELETE_BY_QUERY_BATCH_SIZE);
        countWorkingForSessionsBatch(workingIndex, containerId, batches, 0, 0L, listener);
    }

    private void countWorkingForSessionsBatch(
        String workingIndex,
        String containerId,
        List<List<String>> batches,
        int batchIndex,
        long total,
        ActionListener<Long> listener
    ) {
        if (batchIndex >= batches.size()) {
            listener.onResponse(total);
            return;
        }
        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .must(buildSessionIdMatchQuery(batches.get(batchIndex)));
        countMatching(
            workingIndex,
            query,
            ActionListener
                .wrap(
                    c -> countWorkingForSessionsBatch(workingIndex, containerId, batches, batchIndex + 1, total + c, listener),
                    listener::onFailure
                )
        );
    }

    private void countWorkingForOrphanSessions(
        String containerId,
        String workingIndex,
        List<String> orphanSessionIds,
        long orphanCutoffMillis,
        ActionListener<Long> listener
    ) {
        List<List<String>> batches = partition(orphanSessionIds, DELETE_BY_QUERY_BATCH_SIZE);
        countWorkingForOrphanBatch(containerId, workingIndex, batches, 0, orphanCutoffMillis, 0L, listener);
    }

    private void countWorkingForOrphanBatch(
        String containerId,
        String workingIndex,
        List<List<String>> batches,
        int batchIndex,
        long orphanCutoffMillis,
        long total,
        ActionListener<Long> listener
    ) {
        if (batchIndex >= batches.size()) {
            listener.onResponse(total);
            return;
        }
        // Mirrors deleteOrphanBatch's query exactly (session-id match + epoch-aware created_time cutoff).
        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .must(buildSessionIdMatchQuery(batches.get(batchIndex)))
            .must(buildEpochAwareCutoffQuery(CREATED_TIME_FIELD, orphanCutoffMillis));
        countMatching(
            workingIndex,
            query,
            ActionListener
                .wrap(
                    c -> countWorkingForOrphanBatch(
                        containerId,
                        workingIndex,
                        batches,
                        batchIndex + 1,
                        orphanCutoffMillis,
                        total + c,
                        listener
                    ),
                    listener::onFailure
                )
        );
    }

    private void countEmptyNamespaceWorking(String containerId, String workingIndex, long cutoffMillis, ActionListener<Long> listener) {
        // Mirrors deleteEmptyNamespaceWorkingMemory's query exactly.
        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .mustNot(QueryBuilders.existsQuery(SESSION_ID_FIELD))
            .mustNot(QueryBuilders.existsQuery(NAMESPACE_FIELD + "." + SESSION_ID_FIELD))
            .mustNot(QueryBuilders.existsQuery(NAMESPACE_FIELD + "." + USER_ID_FIELD))
            .mustNot(QueryBuilders.existsQuery(NAMESPACE_FIELD + "." + AGENT_ID_FIELD))
            .must(buildEpochAwareCutoffQuery(CREATED_TIME_FIELD, cutoffMillis));
        countMatching(workingIndex, query, listener);
    }

    private void countMatching(String index, QueryBuilder query, ActionListener<Long> listener) {
        SearchRequest request = new SearchRequest(index);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        request.source(new SearchSourceBuilder().query(query).size(0).trackTotalHits(true));
        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client
                .search(
                    request,
                    ActionListener.wrap(response -> listener.onResponse(response.getHits().getTotalHits().value()), listener::onFailure)
                );
        }
    }

    private void onJobComplete() {
        isRunning.set(false);
        log.info("Memory retention job completed");
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}

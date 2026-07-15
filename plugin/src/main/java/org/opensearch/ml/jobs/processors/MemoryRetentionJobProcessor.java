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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.memorycontainer.RetentionRule;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
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
    private static final int ORPHAN_SESSION_BATCH_SIZE = 1000;
    private static final int ORPHAN_ENUMERATION_PAGE_SIZE = 1000;

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

        processContainer(hits[index], ActionListener.wrap(v -> scheduleNext(hits, index + 1, nextPageSortValues), e -> {
            log.error("Failed processing container [{}]", hits[index].getId(), e);
            scheduleNext(hits, index + 1, nextPageSortValues);
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

    private void processContainer(SearchHit hit, ActionListener<Void> listener) {
        String containerId = hit.getId();
        try {
            Map<String, Object> source = hit.getSourceAsMap();
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) source.get(MEMORY_STORAGE_CONFIG_FIELD);

            if (configMap == null) {
                log.warn("Container [{}] has no configuration field, skipping", containerId);
                listener.onResponse(null);
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
                    listener.onResponse(null);
                    return;
                }
                applyDefaultRetentionPolicy(config, containerId, ActionListener.wrap(persisted -> {
                    if (persisted) {
                        executeRetentionPipeline(config, containerId, listener);
                    } else {
                        listener.onResponse(null);
                    }
                }, listener::onFailure));
            } else {
                executeRetentionPipeline(config, containerId, listener);
            }
        } catch (Exception e) {
            log.error("Error parsing configuration for container [{}]", containerId, e);
            listener.onResponse(null);
        }
    }

    private void executeRetentionPipeline(MemoryConfiguration config, String containerId, ActionListener<Void> listener) {
        executeSessionRetention(
            config,
            containerId,
            ActionListener
                .wrap(
                    v -> executeLongTermRetention(
                        config,
                        containerId,
                        ActionListener
                            .wrap(
                                v2 -> executeHistoryRetention(
                                    config,
                                    containerId,
                                    ActionListener.wrap(v3 -> executeWorkingMemoryTTL(config, containerId, listener), listener::onFailure)
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
    private void applyDefaultRetentionPolicy(MemoryConfiguration config, String containerId, ActionListener<Boolean> listener) {
        int sessionRetentionDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_DEFAULT_SESSION_RETENTION_DAYS);
        int sessionMaxCount = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_DEFAULT_SESSION_MAX_COUNT);
        int longTermMaxCount = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_DEFAULT_LONG_TERM_MAX_COUNT);
        int historyMaxCount = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_DEFAULT_HISTORY_MAX_COUNT);

        boolean anyConfigured = sessionRetentionDays > 0 || sessionMaxCount > 0 || longTermMaxCount > 0 || historyMaxCount > 0;
        if (!anyConfigured) {
            log
                .debug(
                    "Container [{}] has no retention policy and no admin default retention settings are configured; skipping",
                    containerId
                );
            listener.onResponse(false);
            return;
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

        config.setRetentionPolicy(defaultPolicy);

        log
            .info(
                "[MemoryRetentionJob] container={} auto-applying default retention policy: sessions={}/{}d, long-term={}, history={}",
                containerId,
                sessionMaxCount,
                sessionRetentionDays,
                longTermMaxCount,
                historyMaxCount
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

    private void executeSessionRetention(MemoryConfiguration config, String containerId, ActionListener<Void> listener) {
        String sessionIndex = config.getIndexName(MemoryType.SESSIONS);
        if (sessionIndex == null) {
            log.debug("Session index is null for container [{}], skipping session retention", containerId);
            listener.onResponse(null);
            return;
        }

        Map<MemoryType, RetentionRule> retentionPolicy = config.getRetentionPolicy();
        if (retentionPolicy == null || !retentionPolicy.containsKey(MemoryType.SESSIONS)) {
            listener.onResponse(null);
            return;
        }

        RetentionRule sessionRule = retentionPolicy.get(MemoryType.SESSIONS);
        if (sessionRule == null) {
            listener.onResponse(null);
            return;
        }

        identifyExpiredSessions(config, containerId, sessionIndex, sessionRule, ActionListener.wrap(expiredSessionIds -> {
            if (expiredSessionIds.isEmpty()) {
                log.info("[MemoryRetentionJob] container={} sessions_expired=0", containerId);
                listener.onResponse(null);
                return;
            }

            log.info("[MemoryRetentionJob] container={} sessions_expired={}", containerId, expiredSessionIds.size());

            cascadeDeleteWorkingMemory(
                config,
                containerId,
                expiredSessionIds,
                ActionListener
                    .wrap(deletedCount -> deleteSessionDocuments(config, sessionIndex, expiredSessionIds, listener), listener::onFailure)
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

    private void identifyCountBasedExpiredSessions(
        MemoryConfiguration config,
        String containerId,
        String sessionIndex,
        int maxCount,
        ActionListener<Set<String>> listener
    ) {
        // Check if pinned count alone exceeds max_count (fire-and-forget warning)
        checkPinnedExceedsMaxCount(sessionIndex, containerId, "sessions", maxCount);

        // Walk sessions sorted by created_time DESC (newest first). max_count is a cap on total
        // sessions by creation order; retention_days handles activity-based eviction separately.
        Set<String> expiredIds = new HashSet<>();
        identifyCountBasedPage(containerId, sessionIndex, maxCount, expiredIds, 0, null, listener);
    }

    private void identifyCountBasedPage(
        String containerId,
        String sessionIndex,
        int maxCount,
        Set<String> expiredIds,
        int seenSoFar,
        Object[] searchAfter,
        ActionListener<Set<String>> listener
    ) {
        SearchRequest request = new SearchRequest(sessionIndex);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
            .mustNot(QueryBuilders.termQuery(PINNED_FIELD, true));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(query)
            .size(CONTAINER_PAGE_SIZE)
            .sort(CREATED_TIME_FIELD, SortOrder.DESC)
            .sort("_id", SortOrder.ASC)
            .fetchSource(false);

        if (searchAfter != null) {
            sourceBuilder.searchAfter(searchAfter);
        }

        request.source(sourceBuilder);

        try (ThreadContext.StoredContext ignored = client.threadPool().getThreadContext().stashContext()) {
            client.search(request, ActionListener.wrap(response -> {
                SearchHit[] hits = response.getHits().getHits();
                int currentSeen = seenSoFar;

                for (SearchHit hit : hits) {
                    currentSeen++;
                    if (currentSeen > maxCount) {
                        expiredIds.add(hit.getId());
                    }
                }

                if (hits.length == CONTAINER_PAGE_SIZE) {
                    Object[] nextSearchAfter = hits[hits.length - 1].getSortValues();
                    identifyCountBasedPage(containerId, sessionIndex, maxCount, expiredIds, currentSeen, nextSearchAfter, listener);
                } else {
                    listener.onResponse(expiredIds);
                }
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

        DeleteByQueryRequest dbq = new DeleteByQueryRequest(workingMemoryIndex);
        dbq.setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        dbq
            .setQuery(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
                    .must(QueryBuilders.termsQuery(NAMESPACE_FIELD + ".session_id", batch))
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

    private void executeLongTermRetention(MemoryConfiguration config, String containerId, ActionListener<Void> listener) {
        String longTermIndex = config.getIndexName(MemoryType.LONG_TERM);
        if (longTermIndex == null) {
            listener.onResponse(null);
            return;
        }

        Map<MemoryType, RetentionRule> retentionPolicy = config.getRetentionPolicy();
        if (retentionPolicy == null || !retentionPolicy.containsKey(MemoryType.LONG_TERM)) {
            listener.onResponse(null);
            return;
        }

        RetentionRule rule = retentionPolicy.get(MemoryType.LONG_TERM);
        if (rule == null) {
            listener.onResponse(null);
            return;
        }

        ActionListener<Long> timeBasedDone = ActionListener.wrap(timeDeleted -> {
            if (rule.getMaxCount() != null) {
                executeLongTermMaxCount(longTermIndex, containerId, rule.getMaxCount(), ActionListener.wrap(countDeleted -> {
                    long total = (timeDeleted != null ? timeDeleted : 0L) + countDeleted;
                    log.info("[MemoryRetentionJob] container={} long_term_deleted={}", containerId, total);
                    listener.onResponse(null);
                }, listener::onFailure));
            } else {
                log.info("[MemoryRetentionJob] container={} long_term_deleted={}", containerId, timeDeleted != null ? timeDeleted : 0L);
                listener.onResponse(null);
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
                // Step 2: search for oldest excess docs by created_time ASC
                collectOldestDocIds(longTermIndex, containerId, CREATED_TIME_FIELD, excess, ActionListener.wrap(idsToDelete -> {
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
        Set<String> collected = new HashSet<>();
        collectOldestDocIdsPage(index, containerId, timeField, limit, collected, null, listener);
    }

    private void collectOldestDocIdsPage(
        String index,
        String containerId,
        String timeField,
        int limit,
        Set<String> collected,
        Object[] searchAfter,
        ActionListener<Set<String>> listener
    ) {
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
                        listener.onResponse(collected);
                        return;
                    }
                }

                if (hits.length == pageSize && collected.size() < limit) {
                    Object[] nextSearchAfter = hits[hits.length - 1].getSortValues();
                    collectOldestDocIdsPage(index, containerId, timeField, limit, collected, nextSearchAfter, listener);
                } else {
                    listener.onResponse(collected);
                }
            }, listener::onFailure));
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

    private void executeHistoryRetention(MemoryConfiguration config, String containerId, ActionListener<Void> listener) {
        String historyIndex = config.getIndexName(MemoryType.HISTORY);
        if (historyIndex == null) {
            listener.onResponse(null);
            return;
        }

        Map<MemoryType, RetentionRule> retentionPolicy = config.getRetentionPolicy();
        if (retentionPolicy == null || !retentionPolicy.containsKey(MemoryType.HISTORY)) {
            listener.onResponse(null);
            return;
        }

        RetentionRule rule = retentionPolicy.get(MemoryType.HISTORY);
        if (rule == null || rule.getMaxCount() == null) {
            listener.onResponse(null);
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
                    listener.onResponse(null);
                    return;
                }

                int excess = (int) (totalCount - maxCount);
                // Step 2: search oldest excess docs by created_time ASC (history uses created_time)
                collectOldestDocIds(historyIndex, containerId, CREATED_TIME_FIELD, excess, ActionListener.wrap(idsToDelete -> {
                    if (idsToDelete.isEmpty()) {
                        log.info("[MemoryRetentionJob] container={} history_deleted=0", containerId);
                        listener.onResponse(null);
                        return;
                    }
                    // Step 3: bulk delete those IDs
                    deleteDocumentsBatch(historyIndex, new ArrayList<>(idsToDelete), ActionListener.wrap(deleted -> {
                        log.info("[MemoryRetentionJob] container={} history_deleted={}", containerId, deleted);
                        listener.onResponse(null);
                    }, listener::onFailure));
                }, listener::onFailure));
            }, listener::onFailure));
        }
    }

    private void executeWorkingMemoryTTL(MemoryConfiguration config, String containerId, ActionListener<Void> listener) {
        if (!config.isDisableSession()) {
            listener.onResponse(null);
            return;
        }

        String workingIndex = config.getIndexName(MemoryType.WORKING);
        if (workingIndex == null) {
            listener.onResponse(null);
            return;
        }

        int ttlDays = clusterService.getClusterSettings().get(ML_COMMONS_MEMORY_WORKING_MEMORY_TTL_DAYS);
        long cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ttlDays);

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
                listener.onResponse(null);
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

    private void sweepOrphansForContainer(
        MemoryConfiguration config,
        String containerId,
        String workingIndex,
        String sessionsIndex,
        ActionListener<Void> listener
    ) {
        Set<String> sessionIds = new HashSet<>();
        enumerateSessionIdsFromWorkingMemory(workingIndex, containerId, sessionIds, null, ActionListener.wrap(collectedIds -> {
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
        }, listener::onFailure));
    }

    private void enumerateSessionIdsFromWorkingMemory(
        String workingIndex,
        String containerId,
        Set<String> sessionIds,
        Object[] searchAfterValues,
        ActionListener<Set<String>> listener
    ) {
        SearchRequest request = new SearchRequest(workingIndex);
        request.indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);

        BoolQueryBuilder query = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.existsQuery(NAMESPACE_FIELD + "." + SESSION_ID_FIELD))
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
                                    + " Remaining orphans will be caught on next run.",
                                containerId,
                                ORPHAN_SESSION_ID_CAP
                            );
                        listener.onResponse(sessionIds);
                        return;
                    }
                }

                if (hits.length == ORPHAN_ENUMERATION_PAGE_SIZE) {
                    Object[] nextSearchAfter = hits[hits.length - 1].getSortValues();
                    enumerateSessionIdsFromWorkingMemory(workingIndex, containerId, sessionIds, nextSearchAfter, listener);
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

        DeleteByQueryRequest dbq = new DeleteByQueryRequest(workingIndex);
        dbq.setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        dbq
            .setQuery(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
                    .must(QueryBuilders.termsQuery(NAMESPACE_FIELD + "." + SESSION_ID_FIELD, batch))
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

        DeleteByQueryRequest dbq = new DeleteByQueryRequest(workingIndex);
        dbq.setIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        // Epoch-aware cutoff: legacy docs store created_time in epoch seconds and would all match
        // a plain lt(cutoffMillis) range. See normalizeTimestamp().
        dbq
            .setQuery(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId))
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

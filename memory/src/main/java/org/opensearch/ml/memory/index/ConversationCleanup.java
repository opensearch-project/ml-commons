/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.memory.index;

import java.time.Instant;
import java.util.List;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.memory.ConversationalMemoryHandler;
import org.opensearch.search.SearchHit;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.Requests;

import lombok.extern.log4j.Log4j2;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_CLEANUP_USERNAMES;

/**
 * Service for cleaning up old conversations (memories) from specified usernames that are older than 24 hours.
 * Runs on a scheduled basis every 1 hour.
 */
@Log4j2
public class ConversationCleanup {

    private static final int CLEANUP_INTERVAL_HOURS = 1;
    private static final int CONVERSATION_AGE_HOURS = 24;
    private static final String MEMORY_CLEANUP_THREAD_POOL = "opensearch_ml_memory_cleanup";

    private final ConversationalMemoryHandler cmHandler;
    private final Client client;
    private final ThreadPool threadPool;
    private ClusterService clusterService;
    private volatile List<String> cleanupUsers;

    /**
     * Constructor
     * @param cmHandler Handler for conversational memory operations
     * @param client OpenSearch client
     * @param threadPool Thread pool for scheduling tasks
     * @param clusterService Cluster service for accessing settings
     */
    public ConversationCleanup(ConversationalMemoryHandler cmHandler, Client client, ThreadPool threadPool, ClusterService clusterService) {
        this.cmHandler = cmHandler;
        this.client = client;
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.cleanupUsers = ML_COMMONS_MEMORY_CLEANUP_USERNAMES.get(clusterService.getSettings());
        clusterService
                .getClusterSettings()
                .addSettingsUpdateConsumer(ML_COMMONS_MEMORY_CLEANUP_USERNAMES, it -> cleanupUsers = it);
    }

    /**
     * Start the cleanup job that runs every 1 hour
     */
    public void startCleanupJob() {
        try {
            threadPool.schedule(
                () -> cleanupOldConversations(),
                TimeValue.timeValueHours(CLEANUP_INTERVAL_HOURS),
                MEMORY_CLEANUP_THREAD_POOL
            );
        } catch (Exception e) {
            log.error("Failed to schedule cleanup job, will retry", e);
            // Retry scheduling after a short delay to handle transient failures
            try {
                threadPool.schedule(
                    () -> startCleanupJob(),
                    TimeValue.timeValueMinutes(5),
                    MEMORY_CLEANUP_THREAD_POOL
                );
            } catch (Exception retryException) {
                log.error("Failed to retry scheduling cleanup job", retryException);
            }
        }
    }

    /**
     * Clean up conversations older than 24 hours from configured usernames
     */
    private void cleanupOldConversations() {
        // If no usernames configured, skip cleanup
        if (cleanupUsers == null || cleanupUsers.isEmpty()) {
            log.debug("No cleanup usernames configured, skipping cleanup");
            try {
                startCleanupJob();
            } catch (Exception scheduleException) {
                log.error("Failed to reschedule cleanup job", scheduleException);
            }
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<SearchResponse> searchListener = ActionListener.wrap(searchResponse -> {
                SearchHit[] hits = searchResponse.getHits().getHits();
                if (hits.length == 0) {
                    log.debug("No old conversations found to delete");
                    try {
                        startCleanupJob();
                    } catch (Exception scheduleException) {
                        log.error("Failed to reschedule cleanup job", scheduleException);
                    }
                } else {
                    log.info("Found {} conversations older than {} hours to delete", hits.length, CONVERSATION_AGE_HOURS);
                    deleteConversations(hits);
                }
            }, e -> {
                log.error("Failed to search for old conversations", e);
                try {
                    startCleanupJob();
                } catch (Exception scheduleException) {
                    log.error("Failed to reschedule cleanup job after search error", scheduleException);
                }
            });

            Instant cutoffTime = Instant.now().minusSeconds(CONVERSATION_AGE_HOURS * 3600L);
            SearchRequest searchRequest = Requests.searchRequest(ConversationalIndexConstants.META_INDEX_NAME);
            RangeQueryBuilder rangeQuery = QueryBuilders
                .rangeQuery(ConversationalIndexConstants.META_CREATED_TIME_FIELD)
                .lt(cutoffTime.toString());
            // Filter by usernames from the setting
            org.opensearch.index.query.TermsQueryBuilder userQuery = QueryBuilders
                .termsQuery(ConversationalIndexConstants.USER_FIELD, cleanupUsers);
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(rangeQuery)
                .must(userQuery);
            searchRequest.source().query(boolQuery);
            searchRequest.source().size(1000);

            cmHandler.searchConversations(searchRequest, searchListener);
        } catch (Exception e) {
            log.error("Exception during memory cleanup", e);
            try {
                startCleanupJob();
            } catch (Exception scheduleException) {
                log.error("Failed to reschedule cleanup job after exception", scheduleException);
            }
        }
    }

    /**
     * Delete conversations sequentially, processing them one at a time
     */
    private void deleteConversations(SearchHit[] hits) {
        deleteNextConversation(hits, 0);
    }

    /**
     * Delete the next conversation in the array, then proceed to the next one
     */
    private void deleteNextConversation(SearchHit[] hits, int index) {
        if (index >= hits.length) {
            log.info("Completed deletion of {} old conversations", hits.length);
            try {
                startCleanupJob();
            } catch (Exception e) {
                log.error("Failed to reschedule cleanup job after deletion", e);
            }
            return;
        }

        ConversationMeta conversationMeta = ConversationMeta.fromSearchHit(hits[index]);
        ActionListener<Boolean> deleteListener = ActionListener.wrap(success -> {
            deleteNextConversation(hits, index + 1);
        }, e -> {
            log.error("Error deleting conversation: " + conversationMeta.getId(), e);
            // Continue with next conversation even if this one failed
            deleteNextConversation(hits, index + 1);
        });

        cmHandler.deleteConversation(conversationMeta.getId(), deleteListener);
    }
}


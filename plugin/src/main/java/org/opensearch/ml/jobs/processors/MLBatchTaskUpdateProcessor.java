/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs.processors;

import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLBatchTaskUpdateProcessor extends MLJobProcessor {

    private static final Logger log = LogManager.getLogger(MLBatchTaskUpdateProcessor.class);

    private static MLBatchTaskUpdateProcessor instance;

    public static MLBatchTaskUpdateProcessor getInstance(ClusterService clusterService, Client client, ThreadPool threadPool) {
        if (instance != null) {
            return instance;
        }

        synchronized (MLBatchTaskUpdateProcessor.class) {
            if (instance != null) {
                return instance;
            }

            instance = new MLBatchTaskUpdateProcessor(clusterService, client, threadPool);
            return instance;
        }
    }

    /**
     * Resets the singleton instance. This method is only for testing purposes.
     */
    public static synchronized void reset() {
        instance = null;
    }

    public MLBatchTaskUpdateProcessor(ClusterService clusterService, Client client, ThreadPool threadPool) {
        super(clusterService, client, threadPool);
    }

    @Override
    public void run() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQuery = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery("task_type", MLTaskType.BATCH_PREDICTION))
            .must(QueryBuilders.termQuery("function_name", FunctionName.REMOTE))
            .must(
                QueryBuilders
                    .boolQuery()
                    .should(QueryBuilders.termQuery("state", MLTaskState.RUNNING))
                    .should(QueryBuilders.termQuery("state", MLTaskState.CANCELLING))
            );

        sourceBuilder.query(boolQuery);
        sourceBuilder.size(100);
        sourceBuilder.fetchSource(new String[] { "_id" }, null);

        SearchRequest searchRequest = new SearchRequest(ML_TASK_INDEX);
        searchRequest.source(sourceBuilder);

        client.search(searchRequest, ActionListener.wrap(response -> {
            if (response == null || response.getHits() == null || response.getHits().getHits().length == 0) {
                log.info("No pending tasks found to be polled by the job");
                return;
            }

            SearchHit[] searchHits = response.getHits().getHits();
            for (SearchHit searchHit : searchHits) {
                String taskId = searchHit.getId();
                log.debug("Starting polling for task: {} at {}", taskId, Instant.now());
                MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.builder().taskId(taskId).isUserInitiatedGetTaskRequest(false).build();

                client
                    .execute(
                        MLTaskGetAction.INSTANCE,
                        mlTaskGetRequest,
                        ActionListener
                            .wrap(
                                taskResponse -> log.info("Updated Task status for taskId: {} at {}", taskId, Instant.now()),
                                exception -> log.error("Failed to get task status for task: {}", taskId, exception)
                            )
                    );
            }
        }, e -> {
            if (e instanceof IndexNotFoundException) {
                log.info("No tasks found to be polled by the job");
            } else {
                log.error("Failed to search for tasks to be polled by the job ", e);
            }
        }));
    }
}

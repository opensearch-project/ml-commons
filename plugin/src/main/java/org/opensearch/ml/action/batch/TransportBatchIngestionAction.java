/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.batch;

import static org.opensearch.ml.common.MLTask.ERROR_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTaskState.COMPLETED;
import static org.opensearch.ml.common.MLTaskState.FAILED;
import static org.opensearch.ml.plugin.MachineLearningPlugin.INGEST_THREAD_POOL;
import static org.opensearch.ml.task.MLTaskManager.TASK_SEMAPHORE_TIMEOUT;
import static org.opensearch.ml.utils.MLExceptionUtils.OFFLINE_BATCH_INGESTION_DISABLED_ERR_MSG;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionAction;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionRequest;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionResponse;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.ingest.Ingestable;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.MLExceptionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.jayway.jsonpath.PathNotFoundException;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportBatchIngestionAction extends HandledTransportAction<ActionRequest, MLBatchIngestionResponse> {
    private static final String S3_URI_REGEX = "^s3://([a-zA-Z0-9.-]+)(/.*)?$";
    private static final Pattern S3_URI_PATTERN = Pattern.compile(S3_URI_REGEX);
    public static final String TYPE = "type";
    public static final String SOURCE = "source";
    TransportService transportService;
    MLTaskManager mlTaskManager;
    private final Client client;
    private ThreadPool threadPool;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportBatchIngestionAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLTaskManager mlTaskManager,
        ThreadPool threadPool,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLBatchIngestionAction.NAME, transportService, actionFilters, MLBatchIngestionRequest::new);
        this.transportService = transportService;
        this.client = client;
        this.mlTaskManager = mlTaskManager;
        this.threadPool = threadPool;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLBatchIngestionResponse> listener) {
        MLBatchIngestionRequest mlBatchIngestionRequest = MLBatchIngestionRequest.fromActionRequest(request);
        MLBatchIngestionInput mlBatchIngestionInput = mlBatchIngestionRequest.getMlBatchIngestionInput();
        try {
            if (!mlFeatureEnabledSetting.isOfflineBatchIngestionEnabled()) {
                throw new IllegalStateException(OFFLINE_BATCH_INGESTION_DISABLED_ERR_MSG);
            }
            validateBatchIngestInput(mlBatchIngestionInput);
            MLTask mlTask = MLTask
                .builder()
                .async(true)
                .taskType(MLTaskType.BATCH_INGEST)
                .createTime(Instant.now())
                .lastUpdateTime(Instant.now())
                .state(MLTaskState.CREATED)
                .build();

            mlTaskManager.createMLTask(mlTask, ActionListener.wrap(response -> {
                String taskId = response.getId();
                try {
                    mlTask.setTaskId(taskId);
                    mlTaskManager.add(mlTask);
                    listener.onResponse(new MLBatchIngestionResponse(taskId, MLTaskType.BATCH_INGEST, MLTaskState.CREATED.name()));
                    String ingestType = (String) mlBatchIngestionInput.getDataSources().get(TYPE);
                    Ingestable ingestable = MLEngineClassLoader.initInstance(ingestType.toLowerCase(Locale.ROOT), client, Client.class);
                    threadPool.executor(INGEST_THREAD_POOL).execute(() -> {
                        executeWithErrorHandling(() -> {
                            double successRate = ingestable.ingest(mlBatchIngestionInput);
                            handleSuccessRate(successRate, taskId);
                        }, taskId);
                    });
                } catch (Exception ex) {
                    log.error("Failed in batch ingestion", ex);
                    mlTaskManager
                        .updateMLTask(
                            taskId,
                            Map.of(STATE_FIELD, FAILED, ERROR_FIELD, MLExceptionUtils.getRootCauseMessage(ex)),
                            TASK_SEMAPHORE_TIMEOUT,
                            true
                        );
                    listener.onFailure(ex);
                }
            }, exception -> {
                log.error("Failed to create batch ingestion task", exception);
                listener.onFailure(exception);
            }));
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
            listener
                .onFailure(
                    new OpenSearchStatusException(
                        "IllegalArgumentException in the batch ingestion input: " + e.getMessage(),
                        RestStatus.BAD_REQUEST
                    )
                );
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    protected void executeWithErrorHandling(Runnable task, String taskId) {
        try {
            task.run();
        } catch (PathNotFoundException jsonPathNotFoundException) {
            log.error("Error in jsonParse fields", jsonPathNotFoundException);
            mlTaskManager
                .updateMLTask(
                    taskId,
                    Map.of(STATE_FIELD, FAILED, ERROR_FIELD, jsonPathNotFoundException.getMessage()),
                    TASK_SEMAPHORE_TIMEOUT,
                    true
                );
        } catch (Exception e) {
            log.error("Error in ingest, failed to produce a successRate", e);
            mlTaskManager
                .updateMLTask(
                    taskId,
                    Map.of(STATE_FIELD, FAILED, ERROR_FIELD, MLExceptionUtils.getRootCauseMessage(e)),
                    TASK_SEMAPHORE_TIMEOUT,
                    true
                );
        }
    }

    protected void handleSuccessRate(double successRate, String taskId) {
        if (successRate == 100) {
            mlTaskManager.updateMLTask(taskId, Map.of(STATE_FIELD, COMPLETED), 5000, true);
        } else if (successRate > 0) {
            mlTaskManager
                .updateMLTask(
                    taskId,
                    Map.of(STATE_FIELD, FAILED, ERROR_FIELD, "batch ingestion successful rate is " + successRate),
                    TASK_SEMAPHORE_TIMEOUT,
                    true
                );
        } else {
            mlTaskManager
                .updateMLTask(
                    taskId,
                    Map.of(STATE_FIELD, FAILED, ERROR_FIELD, "batch ingestion successful rate is 0"),
                    TASK_SEMAPHORE_TIMEOUT,
                    true
                );
        }
    }

    private void validateBatchIngestInput(MLBatchIngestionInput mlBatchIngestionInput) {
        if (mlBatchIngestionInput == null
            || mlBatchIngestionInput.getDataSources() == null
            || mlBatchIngestionInput.getDataSources().isEmpty()) {
            throw new IllegalArgumentException("The batch ingest input data source cannot be null");
        }
        Map<String, Object> dataSources = mlBatchIngestionInput.getDataSources();
        if (dataSources.get(TYPE) == null || dataSources.get(SOURCE) == null) {
            throw new IllegalArgumentException("The batch ingest input data source is missing data type or source");
        }
        if (((String) dataSources.get(TYPE)).equalsIgnoreCase("s3")) {
            List<String> s3Uris = (List<String>) dataSources.get(SOURCE);
            if (s3Uris == null || s3Uris.isEmpty()) {
                throw new IllegalArgumentException("The batch ingest input s3Uris is empty");
            }

            // Partition the list into valid and invalid URIs
            Map<Boolean, List<String>> partitionedUris = s3Uris
                .stream()
                .collect(Collectors.partitioningBy(uri -> S3_URI_PATTERN.matcher(uri).matches()));

            List<String> invalidUris = partitionedUris.get(false);

            if (!invalidUris.isEmpty()) {
                throw new IllegalArgumentException("The following batch ingest input S3 URIs are invalid: " + invalidUris);
            }
        }
    }
}

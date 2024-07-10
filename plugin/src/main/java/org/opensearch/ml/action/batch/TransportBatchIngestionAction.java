/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.batch;

import static org.opensearch.ml.common.MLTask.ERROR_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTaskState.COMPLETED;
import static org.opensearch.ml.common.MLTaskState.FAILED;
import static org.opensearch.ml.task.MLTaskManager.TASK_SEMAPHORE_TIMEOUT;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

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
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.MLExceptionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

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

    @Inject
    public TransportBatchIngestionAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLTaskManager mlTaskManager
    ) {
        super(MLBatchIngestionAction.NAME, transportService, actionFilters, MLBatchIngestionRequest::new);
        this.transportService = transportService;
        this.client = client;
        this.mlTaskManager = mlTaskManager;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLBatchIngestionResponse> listener) {
        MLBatchIngestionRequest mlBatchIngestionRequest = MLBatchIngestionRequest.fromActionRequest(request);
        MLBatchIngestionInput mlBatchIngestionInput = mlBatchIngestionRequest.getMlBatchIngestionInput();
        try {
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
                    Ingestable ingestable = MLEngineClassLoader
                        .initInstance(mlBatchIngestionInput.getDataSources().get(TYPE).toLowerCase(), client, Client.class);
                    double successRate = ingestable.ingest(mlBatchIngestionInput);
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

    private void validateBatchIngestInput(MLBatchIngestionInput mlBatchIngestionInput) {
        if (mlBatchIngestionInput == null
            || mlBatchIngestionInput.getDataSources() == null
            || mlBatchIngestionInput.getDataSources().isEmpty()) {
            throw new IllegalArgumentException("The batch ingest input data source cannot be null");
        }
        Map<String, String> dataSources = mlBatchIngestionInput.getDataSources();
        if (dataSources.get(TYPE) == null || dataSources.get(SOURCE) == null) {
            throw new IllegalArgumentException("The batch ingest input data source is missing data type or source");
        }
        if (dataSources.get(TYPE).toLowerCase() == "s3") {
            String s3Uri = dataSources.get(SOURCE);
            if (s3Uri == null || s3Uri.isEmpty()) {
                throw new IllegalArgumentException("The batch ingest input s3Uri is empty");
            }

            if (!S3_URI_PATTERN.matcher(s3Uri).matches()) {
                throw new IllegalArgumentException("The batch ingest input s3Uri is invalid");
            }
        }
    }
}

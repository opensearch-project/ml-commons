/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom.upload;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;

import java.io.File;
import java.security.PrivilegedActionException;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.Model;
import org.opensearch.ml.common.transport.custom.upload.MLUploadInput;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

@Log4j2
public class MLModelUploader {

    public static final int TIMEOUT_IN_MILLIS = 5000;
    private final CustomModelManager customModelManager;
    private final MLIndicesHandler mlIndicesHandler;
    private final MLTaskManager mlTaskManager;
    private final ThreadPool threadPool;
    private final Client client;

    public MLModelUploader(
        CustomModelManager customModelManager,
        MLIndicesHandler mlIndicesHandler,
        MLTaskManager mlTaskManager,
        ThreadPool threadPool,
        Client client
    ) {
        this.customModelManager = customModelManager;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlTaskManager = mlTaskManager;
        this.threadPool = threadPool;
        this.client = client;
    }

    public void uploadModel(MLUploadInput mlUploadInput, MLTask mlTask) {
        Semaphore semaphore = new Semaphore(1);
        String taskId = mlTask.getTaskId();
        mlTaskManager.add(mlTask);

        AtomicInteger uploaded = new AtomicInteger(0);
        threadPool.executor(TASK_THREAD_POOL).execute(() -> {
            try {
                String modelName = mlUploadInput.getName();
                Integer version = mlUploadInput.getVersion();
                customModelManager.downloadAndSplit(modelName, version, mlUploadInput.getUrl(), ActionListener.wrap(nameList -> {
                    mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(res -> {
                        for (String name : nameList) {
                            semaphore.acquire();
                            File file = new File(name);
                            byte[] bytes = Files.toByteArray(file);
                            Model model = new Model();
                            model.setName(FunctionName.KMEANS.name());
                            model.setVersion(1);
                            model.setContent(bytes);
                            int chunkNum = Integer.parseInt(file.getName());
                            MLModel mlModel = MLModel
                                .builder()
                                .name(modelName)
                                .algorithm(FunctionName.CUSTOM)
                                .version(version)
                                .chunkNumber(chunkNum)
                                .totalChunks(nameList.size())
                                .content(Base64.getEncoder().encodeToString(bytes))
                                .build();
                            IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX);
                            indexRequest.id(MLModel.customModelId(modelName, version, chunkNum));
                            indexRequest
                                .source(mlModel.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                            indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                            client.index(indexRequest, ActionListener.wrap(r -> {
                                log.debug("Index model successfully {}, {}", modelName, name);
                                uploaded.getAndIncrement();
                                if (uploaded.get() == nameList.size()) {
                                    mlTaskManager
                                        .updateMLTask(
                                            taskId,
                                            ImmutableMap.of(MLTask.STATE_FIELD, MLTaskState.COMPLETED),
                                            TIMEOUT_IN_MILLIS
                                        );
                                    file.delete();
                                    file.getParentFile().delete();
                                    mlTaskManager.remove(taskId);
                                } else {
                                    file.delete();
                                }
                                semaphore.release();
                            }, e -> {
                                log.error("Failed to index model", e);
                                mlTaskManager
                                    .updateMLTask(
                                        taskId,
                                        ImmutableMap
                                            .of(
                                                MLTask.ERROR_FIELD,
                                                ExceptionUtils.getStackTrace(e),
                                                MLTask.STATE_FIELD,
                                                MLTaskState.FAILED
                                            ),
                                        TIMEOUT_IN_MILLIS
                                    );
                                mlTaskManager.remove(taskId);
                                file.delete();
                                semaphore.release();
                            }));
                        }
                    }, ex -> {
                        log.error("Failed to init model index", ex);
                        mlTaskManager
                            .updateMLTask(
                                taskId,
                                ImmutableMap
                                    .of(MLTask.ERROR_FIELD, ExceptionUtils.getStackTrace(ex), MLTask.STATE_FIELD, MLTaskState.FAILED),
                                TIMEOUT_IN_MILLIS
                            );
                        mlTaskManager.remove(taskId);
                    }));
                }, e -> {
                    log.error("Failed to download model", e);
                    mlTaskManager
                        .updateMLTask(
                            taskId,
                            ImmutableMap.of(MLTask.ERROR_FIELD, ExceptionUtils.getStackTrace(e), MLTask.STATE_FIELD, MLTaskState.FAILED),
                            TIMEOUT_IN_MILLIS
                        );
                    mlTaskManager.remove(taskId);
                }));
            } catch (PrivilegedActionException e) {
                log.error("Failed to upload model ", e);
                mlTaskManager
                    .updateMLTask(
                        taskId,
                        ImmutableMap.of(MLTask.ERROR_FIELD, ExceptionUtils.getStackTrace(e), MLTask.STATE_FIELD, MLTaskState.FAILED),
                        TIMEOUT_IN_MILLIS
                    );
                mlTaskManager.remove(taskId);
            }
        });
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelMetaInput;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.threadpool.ThreadPool;

import java.time.Instant;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;

@Log4j2
public class MLModelMetaUploader {

    private final MLIndicesHandler mlIndicesHandler;
    private final ThreadPool threadPool;
    private final Client client;

    @Inject
    public MLModelMetaUploader(MLIndicesHandler mlIndicesHandler, ThreadPool threadPool, Client client) {
        this.mlIndicesHandler = mlIndicesHandler;
        this.threadPool = threadPool;
        this.client = client;
    }

    public void uploadModelMeta(MLUploadModelMetaInput mlUploadModelMetaInput, ActionListener<String> listener) {
        threadPool.executor(TASK_THREAD_POOL).execute(() -> {
            try {
                String modelName = mlUploadModelMetaInput.getName();
                String version = mlUploadModelMetaInput.getVersion();
                FunctionName functionName = mlUploadModelMetaInput.getFunctionName();
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(res -> {
                        MLModel mlModelMeta = MLModel
                            .builder()
                            .name(modelName)
                            .algorithm(functionName)
                            .version(version)
                            .modelFormat(mlUploadModelMetaInput.getModelFormat())
                            .modelState(MLModelState.UPLOADING)
                            .modelConfig(mlUploadModelMetaInput.getModelConfig())
                            .totalChunks(mlUploadModelMetaInput.getTotalChunks())
                            .modelContentHash(mlUploadModelMetaInput.getModelContentHash())
                            .modelContentSizeInBytes(mlUploadModelMetaInput.getModelContentSizeInBytes())
                            .createdTime(Instant.now())
                            .build();
                        IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX);
                        indexRequest
                            .source(mlModelMeta.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                        client.index(indexRequest, ActionListener.wrap(r -> {
                            log.debug("Index model meta doc successfully {}", modelName);
                            listener.onResponse(r.getId());
                        }, e -> {
                            log.error("Failed to index model meta doc", e);
                            listener.onFailure(e);
                        }));
                    }, ex -> {
                        log.error("Failed to init model index", ex);
                        listener.onFailure(ex);
                    }));
                } catch (Exception e) {
                    log.error("Failed to create model meta doc", e);
                    listener.onFailure(e);
                }
            } catch (final Exception e) {
                log.error("Failed to init model index", e);
                listener.onFailure(e);
            }
        });
    }

}

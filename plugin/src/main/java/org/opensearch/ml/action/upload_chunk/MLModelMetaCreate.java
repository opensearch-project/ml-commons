/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.time.Instant;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaInput;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.threadpool.ThreadPool;

@Log4j2
public class MLModelMetaCreate {

    private final MLIndicesHandler mlIndicesHandler;
    private final ThreadPool threadPool;
    private final Client client;

    @Inject
    public MLModelMetaCreate(MLIndicesHandler mlIndicesHandler, ThreadPool threadPool, Client client) {
        this.mlIndicesHandler = mlIndicesHandler;
        this.threadPool = threadPool;
        this.client = client;
    }

    public void createModelMeta(MLCreateModelMetaInput mlCreateModelMetaInput, ActionListener<String> listener) {
        try {
            String modelName = mlCreateModelMetaInput.getName();
            String version = mlCreateModelMetaInput.getVersion();
            FunctionName functionName = mlCreateModelMetaInput.getFunctionName();
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(res -> {
                    Instant now = Instant.now();
                    MLModel mlModelMeta = MLModel
                        .builder()
                        .name(modelName)
                        .algorithm(functionName)
                        .version(version)
                        .description(mlCreateModelMetaInput.getDescription())
                        .modelFormat(mlCreateModelMetaInput.getModelFormat())
                        .modelState(MLModelState.UPLOADING)
                        .modelConfig(mlCreateModelMetaInput.getModelConfig())
                        .totalChunks(mlCreateModelMetaInput.getTotalChunks())
                        .modelContentHash(mlCreateModelMetaInput.getModelContentHashValue())
                        .modelContentSizeInBytes(mlCreateModelMetaInput.getModelContentSizeInBytes())
                        .createdTime(now)
                        .lastUpdateTime(now)
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
    }

}

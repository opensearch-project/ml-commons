/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.upload_chunk;

import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.*;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkResponse;
import org.opensearch.ml.indices.MLIndicesHandler;

import java.util.Base64;
import java.util.concurrent.Semaphore;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

@Log4j2
public class MLModelChunkUploader {

    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    @Inject
    public MLModelChunkUploader(MLIndicesHandler mlIndicesHandler, Client client, final NamedXContentRegistry xContentRegistry) {
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    public void uploadModel(MLUploadModelChunkInput mlUploadInput, ActionListener<MLUploadModelChunkResponse> listener) {
        final String modelId = mlUploadInput.getModelId();
        // Check the size of the content not to exceed 10 mb
        GetRequest getRequest = new GetRequest(ML_MODEL_INDEX).id(modelId);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.wrap(r -> {
                log.info("Completed Get Model Request, id:{}", modelId);

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        // Use this model to update the chunk count
                        MLModel existingModel = MLModel.parse(parser);
                        existingModel.setModelId(r.getId());
                        if (existingModel.getTotalChunks() <= mlUploadInput.getChunkNumber()) {
                            throw new Exception("Chunk number exceeds total chunks");
                        }
                        byte[] bytes = mlUploadInput.getContent();
                        if (bytes == null || bytes.length == 0 || isChunkExceeding10MB(bytes.length)) {
                            throw new Exception("Chunk size either 0 or exceeds 10MB");
                        }
                        mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(res -> {
                            int chunkNum = mlUploadInput.getChunkNumber();
                            MLModel mlModel = MLModel
                                .builder()
                                .algorithm(existingModel.getAlgorithm())
                                .modelId(existingModel.getModelId())
                                .modelFormat(existingModel.getModelFormat())
                                .totalChunks(existingModel.getTotalChunks())
                                .algorithm(existingModel.getAlgorithm())
                                .chunkNumber(chunkNum)
                                .content(Base64.getEncoder().encodeToString(bytes))// TODO: performance testing to evaluate what limits to
                                                                                   // place on model size
                                .build();
                            IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX);
                            indexRequest.id(customModelId(mlUploadInput.getModelId(), mlUploadInput.getChunkNumber()));
                            indexRequest
                                .source(mlModel.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                            indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                            client.index(indexRequest, ActionListener.wrap(response -> {
                                log.info("Index model successfully {}, chunk number {}", mlUploadInput.getModelId(), chunkNum + 1);
                                if (existingModel.getTotalChunks() == mlUploadInput.getChunkNumber()) {
                                    Semaphore semaphore = new Semaphore(1);
                                    semaphore.acquire();
                                    MLModel mlModelMeta = MLModel
                                        .builder()
                                        .name(existingModel.getName())
                                        .algorithm(existingModel.getAlgorithm())
                                        .version(existingModel.getVersion())
                                        .modelFormat(existingModel.getModelFormat())
                                        .modelState(MLModelState.UPLOADED)
                                        .modelConfig(existingModel.getModelConfig())
                                        .totalChunks(existingModel.getTotalChunks())
                                        .modelContentHash(existingModel.getModelContentHash())
                                        .modelContentSizeInBytes(existingModel.getModelContentSizeInBytes())
                                        .createdTime(existingModel.getCreatedTime())
                                        .build();
                                    IndexRequest indexRequest1 = new IndexRequest(ML_MODEL_INDEX);
                                    indexRequest1.id(modelId);
                                    indexRequest1
                                        .source(
                                            mlModelMeta
                                                .toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS)
                                        );
                                    indexRequest1.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                                    client.index(indexRequest1, ActionListener.wrap(re -> {
                                        log.debug("Index model successfully {}", existingModel.getName());
                                        semaphore.release();
                                    }, e -> {
                                        log.error("Failed to update model state", e);
                                        semaphore.release();
                                        listener.onFailure(e);
                                    }));
                                }
                                listener.onResponse(new MLUploadModelChunkResponse("Uploaded"));
                            }, e -> {
                                log.error("Failed to upload chunk model", e);
                                listener.onFailure(e);
                            }));
                        }, ex -> {
                            log.error("Failed to init model index", ex);
                            listener.onFailure(ex);
                        }));
                    } catch (Exception e) {
                        log.error("Failed to parse ml model" + r.getId(), e);
                        listener.onFailure(e);
                    }
                } else {
                    listener.onFailure(new MLResourceNotFoundException("Failed to find model"));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    listener.onFailure(new MLResourceNotFoundException("Failed to find model"));
                } else {
                    log.error("Failed to get ML model " + modelId, e);
                    listener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            log.error(e.getMessage());
            listener.onFailure(e);
        }
    }

    public static String customModelId(String modelId, Integer chunkNumber) {
        return modelId + "_" + chunkNumber;
    }

    private boolean isChunkExceeding10MB(final long length) {
        var isChunkExceedsSize = false;
        double fileSizeInKB = length / 1024;
        double fileSizeInMB = fileSizeInKB / 1024;
        if (fileSizeInMB > 10.0d) {
            isChunkExceedsSize = true;
        }
        return isChunkExceedsSize;
    }
}

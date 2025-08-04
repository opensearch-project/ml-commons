/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportUpdateMemoryAction extends HandledTransportAction<ActionRequest, UpdateResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MLModelManager mlModelManager;

    @Inject
    public TransportUpdateMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelManager mlModelManager
    ) {
        super(MLUpdateMemoryAction.NAME, transportService, actionFilters, MLUpdateMemoryRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> actionListener) {
        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest.fromActionRequest(request);
        String memoryContainerId = updateRequest.getMemoryContainerId();
        String memoryId = updateRequest.getMemoryId();
        String newText = updateRequest.getMlUpdateMemoryInput().getText();

        // Get memory container to validate access and get memory index name
        getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Validate access permissions
            User user = RestActionUtils.getUserContext(client);
            if (!checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to update memories in this container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            // Get the memory index name
            String memoryIndexName = container.getMemoryStorageConfig() != null
                ? container.getMemoryStorageConfig().getMemoryIndexName()
                : null;

            if (memoryIndexName == null || memoryIndexName.isEmpty()) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("Memory container does not have a memory index configured", RestStatus.BAD_REQUEST)
                    );
                return;
            }

            // Check if the memory exists first
            GetRequest getRequest = new GetRequest(memoryIndexName, memoryId);
            client.get(getRequest, ActionListener.wrap(getResponse -> {
                if (!getResponse.isExists()) {
                    actionListener.onFailure(new OpenSearchStatusException("Memory not found", RestStatus.NOT_FOUND));
                    return;
                }

                // Prepare the update
                Map<String, Object> updateFields = new HashMap<>();
                updateFields.put(MEMORY_FIELD, newText);
                updateFields.put(LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());

                // Check if we need to regenerate embedding
                MemoryStorageConfig storageConfig = container.getMemoryStorageConfig();
                if (storageConfig != null && storageConfig.isSemanticStorageEnabled()) {
                    // Generate embedding for the new text
                    generateEmbedding(newText, storageConfig, ActionListener.wrap(embedding -> {
                        if (embedding != null) {
                            updateFields.put(MEMORY_EMBEDDING_FIELD, embedding);
                        }
                        // Perform the update with embedding
                        performUpdate(memoryIndexName, memoryId, updateFields, actionListener);
                    }, error -> {
                        log.error("Failed to generate embedding for memory update, proceeding without embedding", error);
                        // Update without embedding if generation fails
                        performUpdate(memoryIndexName, memoryId, updateFields, actionListener);
                    }));
                } else {
                    // No semantic storage, just update the text and timestamp
                    performUpdate(memoryIndexName, memoryId, updateFields, actionListener);
                }
            }, actionListener::onFailure));

        }, actionListener::onFailure));
    }

    private void performUpdate(
        String indexName,
        String memoryId,
        Map<String, Object> updateFields,
        ActionListener<UpdateResponse> listener
    ) {
        UpdateRequest updateRequest = new UpdateRequest(indexName, memoryId).doc(updateFields);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.update(updateRequest, ActionListener.runBefore(listener, context::restore));
        } catch (Exception e) {
            log.error("Failed to update memory {}", memoryId, e);
            listener.onFailure(e);
        }
    }

    private void generateEmbedding(String text, MemoryStorageConfig storageConfig, ActionListener<Object> listener) {
        // Check if embedding model is deployed (for non-REMOTE models)
        String embeddingModelId = storageConfig.getEmbeddingModelId();
        FunctionName embeddingModelType = storageConfig.getEmbeddingModelType();

        mlModelManager.getModel(embeddingModelId, null, null, ActionListener.wrap(model -> {
            // Check if model is deployed (only for non-REMOTE models)
            if (model.getAlgorithm() != FunctionName.REMOTE && model.getModelState() != MLModelState.DEPLOYED) {
                listener.onFailure(new IllegalStateException("Embedding model " + embeddingModelId + " is not deployed"));
                return;
            }

            // Create MLInput for embedding generation
            MLInput mlInput = MLInput
                .builder()
                .algorithm(embeddingModelType)
                .inputDataset(TextDocsInputDataSet.builder().docs(java.util.Arrays.asList(text)).build())
                .build();

            MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest
                .builder()
                .modelId(embeddingModelId)
                .mlInput(mlInput)
                .build();

            client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(predictionResponse -> {
                if (predictionResponse.getOutput() instanceof ModelTensorOutput) {
                    ModelTensorOutput tensorOutput = (ModelTensorOutput) predictionResponse.getOutput();

                    if (embeddingModelType == FunctionName.TEXT_EMBEDDING) {
                        Object embedding = buildDenseEmbeddingFromResponse(tensorOutput);
                        listener.onResponse(embedding);
                    } else if (embeddingModelType == FunctionName.SPARSE_ENCODING) {
                        Object embedding = buildSparseEmbeddingFromResponse(tensorOutput);
                        listener.onResponse(embedding);
                    } else {
                        listener.onFailure(new IllegalStateException("Unsupported embedding model type: " + embeddingModelType));
                    }
                } else {
                    listener.onFailure(new IllegalStateException("Unexpected ML output type"));
                }
            }, listener::onFailure));

        }, error -> {
            log.error("Failed to get embedding model information", error);
            listener.onFailure(error);
        }));
    }

    private Object buildDenseEmbeddingFromResponse(ModelTensorOutput tensorOutput) {
        for (ModelTensor tensor : tensorOutput.getMlModelOutputs().get(0).getMlModelTensors()) {
            if ("sentence_embedding".equals(tensor.getName())) {
                Number[] data = tensor.getData();
                if (data != null && data.length > 0) {
                    float[] floatArray = new float[data.length];
                    for (int i = 0; i < data.length; i++) {
                        floatArray[i] = data[i].floatValue();
                    }
                    return floatArray;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object buildSparseEmbeddingFromResponse(ModelTensorOutput tensorOutput) {
        for (ModelTensor tensor : tensorOutput.getMlModelOutputs().get(0).getMlModelTensors()) {
            Map<String, ?> dataAsMap = tensor.getDataAsMap();
            if (dataAsMap != null && !dataAsMap.isEmpty()) {
                Map<String, Float> sparseEmbedding = new HashMap<>();
                dataAsMap.forEach((k, v) -> {
                    if (v instanceof Number) {
                        sparseEmbedding.put(k, ((Number) v).floatValue());
                    }
                });
                return sparseEmbedding;
            }
        }
        return null;
    }

    private void getMemoryContainer(String memoryContainerId, ActionListener<MLMemoryContainer> listener) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_MEMORY_CONTAINER_INDEX)
            .id(memoryContainerId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMemoryContainer> wrappedListener = ActionListener.runBefore(listener, context::restore);

            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                        wrappedListener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
                    } else {
                        wrappedListener.onFailure(cause);
                    }
                } else {
                    try {
                        if (r.getResponse() != null && r.getResponse().isExists()) {
                            try (
                                XContentParser parser = jsonXContent
                                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, r.getResponse().getSourceAsString())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLMemoryContainer container = MLMemoryContainer.parse(parser);
                                wrappedListener.onResponse(container);
                            }
                        } else {
                            wrappedListener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
                        }
                    } catch (Exception e) {
                        wrappedListener.onFailure(e);
                    }
                }
            });
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private boolean checkMemoryContainerAccess(User user, MLMemoryContainer mlMemoryContainer) {
        // If security is disabled (user is null), allow access
        if (user == null) {
            return true;
        }

        // If user is admin (has all_access role), allow access
        if (user.getRoles() != null && user.getRoles().contains("all_access")) {
            return true;
        }

        // Check if user is the owner
        User owner = mlMemoryContainer.getOwner();
        if (owner != null && owner.getName() != null && owner.getName().equals(user.getName())) {
            return true;
        }

        // Check if user has matching backend roles
        if (owner != null && owner.getBackendRoles() != null && user.getBackendRoles() != null) {
            return owner.getBackendRoles().stream().anyMatch(role -> user.getBackendRoles().contains(role));
        }

        return false;
    }
}

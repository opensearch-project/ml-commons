/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_REQUIRES_LLM_MODEL_ERROR;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoryResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
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
public class TransportAddMemoryAction extends HandledTransportAction<MLAddMemoryRequest, MLAddMemoryResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportAddMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLAddMemoryAction.NAME, transportService, actionFilters, MLAddMemoryRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLAddMemoryRequest request, ActionListener<MLAddMemoryResponse> actionListener) {
        User user = RestActionUtils.getUserContext(client);
        MLAddMemoryInput input = request.getMlAddMemoryInput();

        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException("Memory input is required"));
            return;
        }

        String memoryContainerId = input.getMemoryContainerId();

        if (memoryContainerId == null || memoryContainerId.isEmpty()) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        // Get memory container
        getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Check access
            if (!checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("User doesn't have permissions to add memory to this container", RestStatus.FORBIDDEN)
                    );
                return;
            }

            // Get memory index name
            String indexName = getIndexName(container);
            if (indexName == null) {
                actionListener.onFailure(new IllegalStateException("Memory index not created for this container"));
                return;
            }

            processAndIndexMemory(input, container, indexName, user, actionListener);
        }, actionListener::onFailure));
    }

    private void processAndIndexMemory(
        MLAddMemoryInput input,
        MLMemoryContainer container,
        String indexName,
        User user,
        ActionListener<MLAddMemoryResponse> actionListener
    ) {
        try {
            // Get the first message (currently limited to 1)
            MessageInput message = input.getMessages().get(0);

            // Generate session ID if not provided
            final String sessionId;
            if (input.getSessionId() == null || input.getSessionId().isEmpty()) {
                sessionId = "sess_" + UUID.randomUUID().toString();
            } else {
                sessionId = input.getSessionId();
            }

            // Determine memory type (currently only RAW_MESSAGE)
            MemoryType memoryType = MemoryType.RAW_MESSAGE;

            // Validate infer flag
            Boolean infer = input.getInfer();
            MemoryStorageConfig storageConfig = container.getMemoryStorageConfig();
            boolean hasLlmModel = storageConfig != null && storageConfig.getLlmModelId() != null;

            if (infer != null && infer && !hasLlmModel) {
                actionListener.onFailure(new IllegalArgumentException(INFER_REQUIRES_LLM_MODEL_ERROR));
                return;
            }

            // Set default infer value based on LLM model presence if not specified
            if (infer == null) {
                infer = hasLlmModel;
            }

            boolean needsEmbedding = storageConfig != null && storageConfig.isSemanticStorageEnabled();

            if (needsEmbedding) {
                // Generate embedding first, then save memory
                generateEmbedding(message.getContent(), storageConfig, ActionListener.wrap(embedding -> {
                    saveMemoryWithEmbedding(input, container, indexName, message, sessionId, user, memoryType, embedding, actionListener);
                }, e -> {
                    log.error("Failed to generate embedding, saving memory without embedding", e);
                    // Save without embedding on failure
                    saveMemoryWithEmbedding(input, container, indexName, message, sessionId, user, memoryType, null, actionListener);
                }));
            } else {
                // No embedding needed, save directly
                saveMemoryWithEmbedding(input, container, indexName, message, sessionId, user, memoryType, null, actionListener);
            }
        } catch (Exception e) {
            log.error("Failed to add memory", e);
            actionListener.onFailure(e);
        }
    }

    private void saveMemoryWithEmbedding(
        MLAddMemoryInput input,
        MLMemoryContainer container,
        String indexName,
        MessageInput message,
        String sessionId,
        User user,
        MemoryType memoryType,
        Object embedding,
        ActionListener<MLAddMemoryResponse> actionListener
    ) {
        // Build memory object
        Instant now = Instant.now();
        MLMemory memory = MLMemory
            .builder()
            .sessionId(sessionId)
            .memory(message.getContent())
            .memoryType(memoryType)
            .userId(user != null ? user.getName() : null)
            .agentId(input.getAgentId())
            .role(message.getRole())
            .tags(input.getTags())
            .createdTime(now)
            .lastUpdatedTime(now)
            .memoryEmbedding(embedding)
            .build();

        // Index the memory without ID (auto-generate)
        IndexRequest indexRequest = new IndexRequest(indexName)
            .source(memory.toIndexMap())
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        client.index(indexRequest, ActionListener.wrap(indexResponse -> {
            String generatedId = indexResponse.getId();
            log.info("Successfully indexed message {} in session {} to index {}", generatedId, sessionId, indexName);
            // Return success response
            MLAddMemoryResponse response = MLAddMemoryResponse
                .builder()
                .memoryId(generatedId)
                .sessionId(sessionId)
                .status("created")
                .build();
            actionListener.onResponse(response);
        }, actionListener::onFailure));
    }

    private void generateEmbedding(String message, MemoryStorageConfig storageConfig, ActionListener<Object> listener) {
        if (storageConfig == null || !storageConfig.isSemanticStorageEnabled()) {
            listener.onResponse(null);
            return;
        }

        String embeddingModelId = storageConfig.getEmbeddingModelId();
        FunctionName embeddingModelType = storageConfig.getEmbeddingModelType();

        if (embeddingModelId == null || embeddingModelType == null) {
            log.error("Embedding model configuration is missing");
            listener.onResponse(null);
            return;
        }

        // Create MLInput for text embedding
        MLInput mlInput = MLInput
            .builder()
            .algorithm(embeddingModelType)
            .inputDataset(TextDocsInputDataSet.builder().docs(Arrays.asList(message)).build())
            .build();

        // Create prediction request
        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(embeddingModelId).mlInput(mlInput).build();

        // Execute prediction
        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            try {
                MLOutput mlOutput = response.getOutput();
                if (mlOutput instanceof ModelTensorOutput) {
                    ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
                    Object embedding = null;

                    if (embeddingModelType == FunctionName.TEXT_EMBEDDING) {
                        embedding = buildDenseEmbeddingFromResponse(tensorOutput);
                    } else if (embeddingModelType == FunctionName.SPARSE_ENCODING) {
                        embedding = buildSparseEmbeddingFromResponse(tensorOutput);
                    }

                    listener.onResponse(embedding);
                } else {
                    log.error("Unexpected ML output type: {}", mlOutput.getClass().getName());
                    listener.onResponse(null);
                }
            } catch (Exception e) {
                log.error("Failed to extract embedding from ML output", e);
                listener.onResponse(null);
            }
        }, e -> {
            log.error("Failed to generate embedding", e);
            listener.onResponse(null);
        }));
    }

    private float[] buildDenseEmbeddingFromResponse(ModelTensorOutput tensorOutput) {
        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.debug("No model outputs found in tensor output");
            return null;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.debug("No model tensors found in model output");
            return null;
        }

        // For dense embeddings, look for the sentence_embedding tensor
        for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
            if ("sentence_embedding".equals(tensor.getName()) && tensor.getData() != null) {
                Number[] data = tensor.getData();
                log.debug("Found sentence_embedding tensor with dimension: {}", data.length);

                // Convert Number[] to float[] for proper storage
                float[] floatData = new float[data.length];
                for (int i = 0; i < data.length; i++) {
                    floatData[i] = data[i].floatValue();
                }
                return floatData;
            }
        }

        log.error("No sentence_embedding tensor found for dense embedding");
        return null;
    }

    private Map<String, ?> buildSparseEmbeddingFromResponse(ModelTensorOutput tensorOutput) {
        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.debug("No model outputs found in tensor output");
            return null;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.debug("No model tensors found in model output");
            return null;
        }

        // For sparse embeddings, find the tensor with dataAsMap
        for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
            Map<String, ?> dataMap = tensor.getDataAsMap();
            if (dataMap != null) {
                // Check if sparse embedding is nested in a response field
                if (dataMap.containsKey("response") && dataMap.get("response") instanceof List) {
                    List<?> responseList = (List<?>) dataMap.get("response");
                    if (!responseList.isEmpty() && responseList.get(0) instanceof Map) {
                        Map<String, ?> sparseMap = (Map<String, ?>) responseList.get(0);
                        log.debug("Extracted sparse embedding from nested response with {} tokens", sparseMap.size());
                        return sparseMap;
                    }
                }
                // Otherwise return the direct map
                log.debug("Using direct sparse embedding with {} tokens", dataMap.size());
                return dataMap;
            }
        }

        log.error("No sparse embedding data found");
        return null;
    }

    private void getMemoryContainer(String memoryContainerId, ActionListener<MLMemoryContainer> listener) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
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
        }
    }

    private String getIndexName(MLMemoryContainer container) {
        MemoryStorageConfig config = container.getMemoryStorageConfig();
        if (config != null && config.getMemoryIndexName() != null) {
            return config.getMemoryIndexName();
        }
        return null;
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

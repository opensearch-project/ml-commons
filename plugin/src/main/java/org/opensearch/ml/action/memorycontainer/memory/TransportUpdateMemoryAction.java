/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.helper.MemoryEmbeddingHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.remote.metadata.client.SdkClient;
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
    final MemoryContainerHelper memoryContainerHelper;
    final MemoryEmbeddingHelper memoryEmbeddingHelper;

    @Inject
    public TransportUpdateMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelManager mlModelManager,
        MemoryContainerHelper memoryContainerHelper,
        MemoryEmbeddingHelper memoryEmbeddingHelper
    ) {
        super(MLUpdateMemoryAction.NAME, transportService, actionFilters, MLUpdateMemoryRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
        this.memoryContainerHelper = memoryContainerHelper;
        this.memoryEmbeddingHelper = memoryEmbeddingHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException(MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN)
                );
            return;
        }

        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest.fromActionRequest(request);
        String memoryContainerId = updateRequest.getMemoryContainerId();
        String memoryId = updateRequest.getMemoryId();
        String newText = updateRequest.getMlUpdateMemoryInput().getText();

        // Get memory container to validate access and get memory index name
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Validate access permissions
            User user = RestActionUtils.getUserContext(client);
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to update memories in this container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            // Validate and get memory index name
            if (!memoryContainerHelper.validateMemoryIndexExists(container, "update", actionListener)) {
                return;
            }
            String memoryIndexName = memoryContainerHelper.getMemoryIndexName(container);

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
                    memoryEmbeddingHelper.generateEmbedding(newText, storageConfig, ActionListener.wrap(embedding -> {
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
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.BACKEND_ROLES_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DESCRIPTION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_STORAGE_CONFIG_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAME_FIELD;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
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
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.helper.StrategyMergeHelper;
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
public class TransportUpdateMemoryContainerAction extends HandledTransportAction<ActionRequest, UpdateResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MLModelManager mlModelManager;
    final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportUpdateMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLModelManager mlModelManager,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLUpdateMemoryContainerAction.NAME, transportService, actionFilters, MLUpdateMemoryContainerRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlModelManager = mlModelManager;
        this.memoryContainerHelper = memoryContainerHelper;
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

        MLUpdateMemoryContainerRequest mlUpdateMemoryContainerRequest = MLUpdateMemoryContainerRequest.fromActionRequest(request);
        String memoryContainerId = mlUpdateMemoryContainerRequest.getMemoryContainerId();
        String newName = mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput().getName();
        String newDescription = mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput().getDescription();
        List<String> allowedBackendRoles = mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput().getBackendRoles();
        List<MemoryStrategy> updateStrategies = mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput().getStrategies();

        // Get memory container to validate access
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

            // Prepare the update
            Map<String, Object> updateFields = new HashMap<>();
            if (newName != null) {
                updateFields.put(NAME_FIELD, newName);
            }
            if (newDescription != null) {
                updateFields.put(DESCRIPTION_FIELD, newDescription);
            }
            if (allowedBackendRoles != null) {
                updateFields.put(BACKEND_ROLES_FIELD, allowedBackendRoles);
            }

            // Handle strategy updates if provided
            if (updateStrategies != null && !updateStrategies.isEmpty()) {
                try {
                    MemoryConfiguration currentConfig = container.getConfiguration();
                    List<MemoryStrategy> existingStrategies = currentConfig.getStrategies();

                    // Merge strategies
                    List<MemoryStrategy> mergedStrategies = StrategyMergeHelper.mergeStrategies(existingStrategies, updateStrategies);

                    // Create updated configuration with merged strategies
                    MemoryConfiguration updatedConfig = MemoryConfiguration
                        .builder()
                        .indexPrefix(currentConfig.getIndexPrefix())
                        .llmId(currentConfig.getLlmId())
                        .embeddingModelType(currentConfig.getEmbeddingModelType())
                        .embeddingModelId(currentConfig.getEmbeddingModelId())
                        .dimension(currentConfig.getDimension())
                        .maxInferSize(currentConfig.getMaxInferSize())
                        .strategies(mergedStrategies)
                        .indexSettings(currentConfig.getIndexSettings())
                        .parameters(currentConfig.getParameters())
                        .disableHistory(currentConfig.isDisableHistory())
                        .disableSession(currentConfig.isDisableSession())
                        .useSystemIndex(currentConfig.isUseSystemIndex())
                        .tenantId(currentConfig.getTenantId())
                        .build();

                    updateFields.put(MEMORY_STORAGE_CONFIG_FIELD, updatedConfig);
                    log.info("Merged {} strategy updates for container {}", updateStrategies.size(), memoryContainerId);
                } catch (Exception e) {
                    log.error("Failed to merge strategies for container {}", memoryContainerId, e);
                    actionListener.onFailure(e);
                    return;
                }
            }

            updateFields.put(LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
            performUpdate(ML_MEMORY_CONTAINER_INDEX, memoryContainerId, updateFields, actionListener);
        }, actionListener::onFailure));
    }

    private void performUpdate(
        String indexName,
        String memoryContainerId,
        Map<String, Object> updateFields,
        ActionListener<UpdateResponse> listener
    ) {
        UpdateRequest updateRequest = new UpdateRequest(indexName, memoryContainerId).doc(updateFields);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.update(updateRequest, ActionListener.runBefore(listener, context::restore));
        } catch (Exception e) {
            log.error("Failed to update memory container {}", memoryContainerId, e);
            listener.onFailure(e);
        }
    }
}

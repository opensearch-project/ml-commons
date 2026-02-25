/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.conversation.ActionConstants.ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENTS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BINARY_DATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.METADATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRUCTURED_DATA_BLOB_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRUCTURED_DATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SUMMARY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
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
public class TransportUpdateMemoryAction extends HandledTransportAction<ActionRequest, IndexResponse> {

    final Client client;
    final SdkClient sdkClient;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportUpdateMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLUpdateMemoryAction.NAME, transportService, actionFilters, MLUpdateMemoryRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<IndexResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException(MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN)
                );
            return;
        }

        MLUpdateMemoryRequest updateRequest = MLUpdateMemoryRequest.fromActionRequest(request);
        String memoryContainerId = updateRequest.getMemoryContainerId();
        MemoryType memoryType = updateRequest.getMemoryType();
        String memoryId = updateRequest.getMemoryId();
        String tenantId = updateRequest.getTenantId();

        // Get memory container to validate access and get memory index name
        memoryContainerHelper.getMemoryContainer(memoryContainerId, tenantId, ActionListener.wrap(container -> {
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
            String memoryIndexName = memoryContainerHelper.getMemoryIndexName(container, memoryType);
            if (memoryIndexName == null) {
                actionListener.onFailure(new OpenSearchStatusException("Memory index not found", RestStatus.NOT_FOUND));
                return;
            }
            if (memoryIndexName.endsWith("-memory-history")) {
                actionListener.onFailure(new OpenSearchStatusException("Can't update memory history", RestStatus.NOT_FOUND));
                return;
            }

            // Check if the memory exists first
            GetRequest getRequest = new GetRequest(memoryIndexName, memoryId);
            ActionListener<GetResponse> getResponseActionListener = ActionListener.wrap(getResponse -> {
                if (!getResponse.isExists()) {
                    actionListener.onFailure(new OpenSearchStatusException("Memory not found", RestStatus.NOT_FOUND));
                    return;
                }
                Map<String, Object> originalDoc = getResponse.getSourceAsMap();
                String ownerId = (String) originalDoc.get(OWNER_ID_FIELD);
                if (!memoryContainerHelper.checkMemoryAccess(user, ownerId)) {
                    actionListener
                        .onFailure(
                            new OpenSearchStatusException("User doesn't have permissions to update this memory", RestStatus.FORBIDDEN)
                        );
                    return;
                }

                // Prepare the update
                Map<String, Object> newDoc = constructNewDoc(updateRequest.getMlUpdateMemoryInput(), memoryType, originalDoc);
                IndexRequest indexRequest = new IndexRequest(memoryIndexName).id(memoryId).source(newDoc);
                indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                memoryContainerHelper.indexData(container.getConfiguration(), indexRequest, actionListener);

            }, actionListener::onFailure);
            memoryContainerHelper.getData(container.getConfiguration(), getRequest, getResponseActionListener);

        }, actionListener::onFailure));
    }

    public Map<String, Object> constructNewDoc(MLUpdateMemoryInput input, MemoryType memoryType, Map<String, Object> originalDoc) {
        Map<String, Object> updateFields = new HashMap<>();
        updateFields.putAll(originalDoc);
        Map<String, Object> updateContent = input.getUpdateContent();

        if (memoryType != null) {
            switch (memoryType) {
                case SESSIONS:
                    constructSessionMemUpdateFields(updateFields, updateContent);
                    break;
                case WORKING:
                    constructWorkingMemUpdateFields(updateFields, updateContent);
                    break;
                case LONG_TERM:
                    constructLongTermMemUpdateFields(updateFields, updateContent);
                    break;
                case HISTORY:
                    // History should not be updatable, but handle for completeness
                    break;
            }
        }
        updateFields.put(LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
        return updateFields;
    }

    public Map<String, Object> constructSessionMemUpdateFields(Map<String, Object> updateFields, Map<String, Object> updateContent) {
        if (updateContent.containsKey(SUMMARY_FIELD)) {
            updateFields.put(SUMMARY_FIELD, updateContent.get(SUMMARY_FIELD));
        }
        if (updateContent.containsKey(METADATA_FIELD)) {
            updateFields.put(METADATA_FIELD, updateContent.get(METADATA_FIELD));
        }
        if (updateContent.containsKey(AGENTS_FIELD)) {
            updateFields.put(AGENTS_FIELD, updateContent.get(AGENTS_FIELD));
        }
        if (updateContent.containsKey(ADDITIONAL_INFO_FIELD)) {
            updateFields.put(ADDITIONAL_INFO_FIELD, updateContent.get(ADDITIONAL_INFO_FIELD));
        }
        return updateFields;
    }

    public Map<String, Object> constructWorkingMemUpdateFields(Map<String, Object> updateFields, Map<String, Object> updateContent) {
        if (updateContent.containsKey(MESSAGES_FIELD)) {
            updateFields.put(MESSAGES_FIELD, updateContent.get(MESSAGES_FIELD));
        }
        if (updateContent.containsKey(BINARY_DATA_FIELD)) {
            updateFields.put(BINARY_DATA_FIELD, updateContent.get(BINARY_DATA_FIELD));
        }
        if (updateContent.containsKey(STRUCTURED_DATA_FIELD)) {
            updateFields.put(STRUCTURED_DATA_FIELD, updateContent.get(STRUCTURED_DATA_FIELD));
        }
        if (updateContent.containsKey(STRUCTURED_DATA_BLOB_FIELD)) {
            updateFields.put(STRUCTURED_DATA_BLOB_FIELD, updateContent.get(STRUCTURED_DATA_BLOB_FIELD));
        }
        if (updateContent.containsKey(METADATA_FIELD)) {
            updateFields.put(METADATA_FIELD, updateContent.get(METADATA_FIELD));
        }
        if (updateContent.containsKey(TAGS_FIELD)) {
            updateFields.put(TAGS_FIELD, updateContent.get(TAGS_FIELD));
        }
        return updateFields;
    }

    public Map<String, Object> constructLongTermMemUpdateFields(Map<String, Object> updateFields, Map<String, Object> updateContent) {
        if (updateContent.containsKey(MEMORY_FIELD)) {
            updateFields.put(MEMORY_FIELD, updateContent.get(MEMORY_FIELD));
        }
        if (updateContent.containsKey(TAGS_FIELD)) {
            updateFields.put(TAGS_FIELD, updateContent.get(TAGS_FIELD));
        }
        return updateFields;
    }

}

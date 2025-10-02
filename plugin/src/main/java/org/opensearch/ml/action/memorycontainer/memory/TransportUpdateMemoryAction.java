/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.conversation.ActionConstants.ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

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
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
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
        String memoryType = updateRequest.getMemoryType();
        String memoryId = updateRequest.getMemoryId();

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
                Map<String, Object> updateFields = constructUpdateFields(updateRequest.getMlUpdateMemoryInput(), memoryType, originalDoc);
                IndexRequest indexRequest = new IndexRequest(memoryIndexName).id(memoryId).source(updateFields);
                memoryContainerHelper.indexData(container.getConfiguration(), indexRequest, actionListener);

            }, actionListener::onFailure);
            memoryContainerHelper.getData(container.getConfiguration(), getRequest, getResponseActionListener);

        }, actionListener::onFailure));
    }

    public Map<String, Object> constructUpdateFields(MLUpdateMemoryInput input, String memoryType, Map<String, Object> originalDoc) {
        Map<String, Object> updateFields = new HashMap<>();
        updateFields.putAll(originalDoc);
        Map<String, Object> updateContent = input.getUpdateContent();
        switch (memoryType) {
            case "session":
                constructSessionMemUpdateFields(updateFields, updateContent);
                break;
            case "working":
                constructWorkingMemUpdateFields(updateFields, updateContent);
                break;
            case "long-term":
                constructLongTermMemUpdateFields(updateFields, updateContent);
                break;
            default:
                break;
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

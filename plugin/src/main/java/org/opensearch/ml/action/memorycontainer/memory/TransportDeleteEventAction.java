/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteEventAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteEventRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Transport action for deleting an event from a memory container
 */
@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportDeleteEventAction extends HandledTransportAction<MLDeleteEventRequest, DeleteResponse> {

    final Client client;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportDeleteEventAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLDeleteEventAction.NAME, transportService, actionFilters, MLDeleteEventRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLDeleteEventRequest request, ActionListener<DeleteResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        String memoryContainerId = request.getMemoryContainerId();
        String eventId = request.getEventId();

        if (StringUtils.isBlank(memoryContainerId)) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        if (StringUtils.isBlank(eventId)) {
            actionListener.onFailure(new IllegalArgumentException("Event ID is required"));
            return;
        }

        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to delete from this memory container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            deleteEventFromIndex(eventId, container.getConfiguration(), actionListener);
        }, actionListener::onFailure));
    }

    private void deleteEventFromIndex(String eventId, MemoryConfiguration configuration, ActionListener<DeleteResponse> actionListener) {
        try {
            String eventIndexName = configuration.getShortTermMemoryIndexName();
            DeleteRequest deleteRequest = new DeleteRequest(eventIndexName, eventId);

            client.delete(deleteRequest, ActionListener.wrap(deleteResponse -> {
                if (deleteResponse.getResult() == DeleteResponse.Result.NOT_FOUND) {
                    actionListener.onFailure(new OpenSearchStatusException("Event not found", RestStatus.NOT_FOUND));
                    return;
                }
                actionListener.onResponse(deleteResponse);
            }, actionListener::onFailure));
        } catch (Exception e) {
            log.error("Failed to delete event", e);
            actionListener.onFailure(e);
        }
    }
}

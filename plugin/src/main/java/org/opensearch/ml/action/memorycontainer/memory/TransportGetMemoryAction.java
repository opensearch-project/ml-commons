/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportGetMemoryAction extends HandledTransportAction<ActionRequest, MLGetMemoryResponse> {

    final Client client;
    final NamedXContentRegistry xContentRegistry;
    final MemoryContainerHelper memoryContainerHelper;
    final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportGetMemoryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MemoryContainerHelper memoryContainerHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLGetMemoryAction.NAME, transportService, actionFilters, MLGetMemoryRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.memoryContainerHelper = memoryContainerHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLGetMemoryResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }
        MLGetMemoryRequest getRequest = MLGetMemoryRequest.fromActionRequest(request);
        String memoryContainerId = getRequest.getMemoryContainerId();
        String memoryType = getRequest.getMemoryType();
        String memoryId = getRequest.getMemoryId();

        // Get memory container to validate access and get memory index name
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Validate access permissions
            User user = RestActionUtils.getUserContext(client);
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to get memories in this container",
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

            ActionListener<GetResponse> getResponseActionListener = ActionListener.wrap(getResponse -> {
                if (!getResponse.isExists()) {
                    actionListener.onFailure(new OpenSearchStatusException("Memory not found", RestStatus.NOT_FOUND));
                    return;
                }
                String ownerId = (String) getResponse.getSourceAsMap().get(OWNER_ID_FIELD);
                if (!memoryContainerHelper.checkMemoryAccess(user, ownerId)) {
                    actionListener
                        .onFailure(
                            new OpenSearchStatusException("User doesn't have permissions to update this memory", RestStatus.FORBIDDEN)
                        );
                    return;
                }
                actionListener.onResponse(MLGetMemoryResponse.fromGetResponse(getResponse, memoryType));
            }, actionListener::onFailure);
            // Get the memory document
            GetRequest getMemoryRequest = new GetRequest(memoryIndexName, memoryId);
            memoryContainerHelper.getData(container.getConfiguration(), getMemoryRequest, getResponseActionListener);
        }, actionListener::onFailure));
    }

}

/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.memory.action.conversation;

import static org.opensearch.core.rest.RestStatus.FORBIDDEN;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_REST_ACCESS_RESTRICTED_BACKEND_ROLES;

import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.memory.ConversationalMemoryHandler;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * The CreateConversationAction that actually does all of the work
 */
@Log4j2
public class CreateConversationTransportAction extends HandledTransportAction<CreateConversationRequest, CreateConversationResponse> {

    private ConversationalMemoryHandler cmHandler;
    private Client client;
    private ClusterService clusterService;

    private volatile boolean featureIsEnabled;
    private volatile List<String> restrictedBackendRoles;

    /**
     * Constructor
     * @param transportService for inter-node communications
     * @param actionFilters for filtering actions
     * @param cmHandler Handler for conversational memory operations
     * @param client OS Client for dealing with OS
     * @param clusterService for some cluster ops
     */
    @Inject
    public CreateConversationTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        OpenSearchConversationalMemoryHandler cmHandler,
        Client client,
        ClusterService clusterService
    ) {
        super(CreateConversationAction.NAME, transportService, actionFilters, CreateConversationRequest::new);
        this.cmHandler = cmHandler;
        this.client = client;
        this.clusterService = clusterService;
        this.featureIsEnabled = MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.get(clusterService.getSettings());
        this.restrictedBackendRoles = ML_COMMONS_MEMORY_REST_ACCESS_RESTRICTED_BACKEND_ROLES.get(clusterService.getSettings());
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED, it -> featureIsEnabled = it);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MEMORY_REST_ACCESS_RESTRICTED_BACKEND_ROLES, it -> restrictedBackendRoles = it);
    }

    @Override
    protected void doExecute(Task task, CreateConversationRequest request, ActionListener<CreateConversationResponse> actionListener) {
        if (!featureIsEnabled) {
            actionListener.onFailure(new OpenSearchException(ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE));
            return;
        }
        // Check if request came from REST and user should be blocked
        if (request.isFromRest() && shouldBlockRestAccessForMemoryCreation(client, restrictedBackendRoles)) {
            actionListener.onFailure(
                new OpenSearchStatusException(
                    "You are not permitted to create conversations.",
                    FORBIDDEN
                )
            );
            return;
        }
        String name = request.getName();
        String applicationType = request.getApplicationType();
        Map<String, String> additionalInfos = request.getAdditionalInfos();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            ActionListener<CreateConversationResponse> internalListener = ActionListener.runBefore(actionListener, () -> context.restore());
            ActionListener<String> al = ActionListener.wrap(r -> {
                internalListener.onResponse(new CreateConversationResponse(r));
            }, e -> {
                log.error("Failed to create new memory with name " + request.getName(), e);
                internalListener.onFailure(e);
            });

            if (name == null) {
                cmHandler.createConversation(al);
            } else {
                cmHandler.createConversation(name, applicationType, additionalInfos, al);
            }
        } catch (Exception e) {
            log.error("Failed to create new memory with name " + request.getName(), e);
            actionListener.onFailure(e);
        }
    }

    /**
     * Check if a user should be blocked from REST access for creating conversations/interactions.
     * Users with any of the specified backend roles will be blocked from REST access but can still use transport actions.
     * @param client Client containing user info
     * @param restrictedBackendRoles List of backend roles that should be blocked from REST access (null or empty to disable blocking)
     * @return true if the user should be blocked from REST access, false otherwise
     */
    private boolean shouldBlockRestAccessForMemoryCreation(Client client, List<String> restrictedBackendRoles) {
        if (restrictedBackendRoles == null || restrictedBackendRoles.isEmpty()) {
            return false;
        }
        String userStr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        User user = User.parse(userStr);
        if (user == null) {
            return false;
        }
        List<String> userBackendRoles = user.getBackendRoles();
        if (userBackendRoles == null || userBackendRoles.isEmpty()) {
            return false;
        }
        // Check if user has any of the restricted backend roles
        for (String restrictedRole : restrictedBackendRoles) {
            if (userBackendRoles.contains(restrictedRole)) {
                return true;
            }
        }
        return false;
    }

}

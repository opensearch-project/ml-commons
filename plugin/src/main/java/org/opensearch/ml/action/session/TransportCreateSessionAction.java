/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.session;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.io.IOException;
import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MLMemorySession;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.session.MLCreateSessionAction;
import org.opensearch.ml.common.transport.session.MLCreateSessionInput;
import org.opensearch.ml.common.transport.session.MLCreateSessionRequest;
import org.opensearch.ml.common.transport.session.MLCreateSessionResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action for creating a session
 */
@Log4j2
public class TransportCreateSessionAction extends HandledTransportAction<MLCreateSessionRequest, MLCreateSessionResponse> {

    private final Client client;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportCreateSessionAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLCreateSessionAction.NAME, transportService, actionFilters, MLCreateSessionRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLCreateSessionRequest request, ActionListener<MLCreateSessionResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        MLCreateSessionInput input = request.getMlCreateSessionInput();

        // Validate tenant ID
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, input.getTenantId(), actionListener)) {
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        String ownerId = memoryContainerHelper.getOwnerId(user);
        input.setOwnerId(ownerId);
        String tenantId = input.getTenantId();

        String memoryContainerId = input.getMemoryContainerId();
        if (StringUtils.isBlank(memoryContainerId)) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("User doesn't have permissions to add memory to this container", RestStatus.FORBIDDEN)
                    );
                return;
            }
            createNewSession(input, container, user, tenantId, actionListener);
        }, actionListener::onFailure));
    }

    private void createNewSession(
        MLCreateSessionInput input,
        MLMemoryContainer container,
        User user,
        String tenantId,
        ActionListener<MLCreateSessionResponse> actionListener
    ) {
        Instant now = Instant.now();
        MLMemorySession session = MLMemorySession
            .builder()
            .ownerId(input.getOwnerId())
            .summary(input.getSummary())
            .createdTime(now)
            .lastUpdateTime(now)
            .metadata(input.getMetadata())
            .agents(input.getAgents())
            .additionalInfo(input.getAdditionalInfo())
            .namespace(input.getNamespace())
            .tenantId(tenantId)
            .build();
        IndexRequest indexRequest = new IndexRequest(container.getConfiguration().getSessionIndexName());
        String sessionId = input.getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            indexRequest.id(sessionId);
        }
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            session.toXContent(builder, ToXContent.EMPTY_PARAMS);
            indexRequest.source(builder);
            memoryContainerHelper.indexData(container.getConfiguration(), indexRequest, ActionListener.wrap(r -> {
                MLCreateSessionResponse response = MLCreateSessionResponse.builder().sessionId(r.getId()).status("created").build();
                actionListener.onResponse(response);
            }, e -> { actionListener.onFailure(e); }));
        } catch (IOException e) {
            actionListener.onFailure(e);
        }
    }

}

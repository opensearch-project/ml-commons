/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.core.xcontent.XContentParserUtils;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateAction;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateInput;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateRequest;
import org.opensearch.ml.helper.NameUniquenessHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateAgentTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public UpdateAgentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLAgentUpdateAction.NAME, transportService, actionFilters, MLAgentUpdateRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> actionListener) {
        MLAgentUpdateRequest mlAgentUpdateRequest = MLAgentUpdateRequest.fromActionRequest(request);
        MLAgentUpdateInput mlAgentUpdateInput = mlAgentUpdateRequest.getMlAgentUpdateInput();
        String agentId = mlAgentUpdateInput.getAgentId();
        String tenantId = mlAgentUpdateInput.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_AGENT_INDEX)
            .id(agentId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<UpdateResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                log.debug("Completed Get Agent request for Agent ID {}", agentId);
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    log.error("Failed to get ML Agent {}", agentId, cause);
                    wrappedListener.onFailure(cause);
                } else {
                    try {
                        GetResponse getResponse = r.getResponse();
                        if (getResponse == null || !getResponse.isExists()) {
                            wrappedListener
                                .onFailure(new OpenSearchStatusException("Failed to get agent with ID " + agentId, RestStatus.NOT_FOUND));
                        }
                        XContentParser parser = JsonXContent.jsonXContent
                            .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString());
                        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLAgent retrievedAgent = MLAgent.parse(parser);

                        if (TenantAwareHelper
                            .validateTenantResource(mlFeatureEnabledSetting, tenantId, retrievedAgent.getTenantId(), wrappedListener)) {
                            if (retrievedAgent.getIsHidden() && !isSuperAdmin) {
                                wrappedListener
                                    .onFailure(
                                        new OpenSearchStatusException(
                                            "User does not have privilege to perform this operation on this agent",
                                            RestStatus.FORBIDDEN
                                        )
                                    );
                            } else {
                                validateAgentNameUniquenessForUpdate(
                                    mlAgentUpdateInput,
                                    retrievedAgent,
                                    ActionListener
                                        .wrap(
                                            unused -> updateAgent(agentId, mlAgentUpdateInput, retrievedAgent, wrappedListener),
                                            wrappedListener::onFailure
                                        )
                                );
                            }
                        } else {
                            log.error("Failed to validate tenant for Agent ID {}", agentId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to get ML agent {}", agentId);
                        wrappedListener.onFailure(e);
                    }
                }
            });
        }
    }

    private void updateAgent(
        String agentId,
        MLAgentUpdateInput updateInput,
        MLAgent originalAgent,
        ActionListener<UpdateResponse> wrappedListener
    ) {
        Instant now = Instant.now();
        updateInput.setLastUpdateTime(now);

        MLAgent updatedAgent = updateInput.toMLAgent(originalAgent);

        UpdateDataObjectRequest updateDataObjectRequest = UpdateDataObjectRequest
            .builder()
            .index(ML_AGENT_INDEX)
            .id(agentId)
            .dataObject(updatedAgent)
            .build();

        sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((r, throwable) -> {
            log.debug("Completed Update Agent request for Agent ID {}", agentId);
            if (throwable != null) {
                Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                log.error("Failed to update ML Agent {}", agentId, cause);
                wrappedListener.onFailure(cause);
            } else {
                try {
                    UpdateResponse updateResponse = r.updateResponse();
                    if (updateResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                        log.info("Successfully updated ML agent {}", agentId);
                        wrappedListener.onResponse(updateResponse);
                    }
                } catch (Exception e) {
                    log.error("Failed to update ML agent {}", agentId, e);
                    wrappedListener.onFailure(e);
                }
            }
        });
    }

    /**
     * When {@code plugins.ml_commons.agent_name_uniqueness_enabled} is enabled and the update
     * would rename the agent to a new value, reject the update if another agent in the same
     * tenant already has that name. Skipped if the setting is off, if the update omits a new
     * name (or provides a blank one), or if the new name equals the existing name (so a PUT
     * that doesn't actually rename does not 409 against itself). Mirrors the gate shape used
     * by {@code TransportUpdateModelGroupAction#updateModelGroup}.
     *
     * <p>Same best-effort semantics as the register path: two concurrent updates racing on the
     * same name can both see zero hits and both succeed.
     */
    private void validateAgentNameUniquenessForUpdate(
        MLAgentUpdateInput updateInput,
        MLAgent originalAgent,
        ActionListener<Void> listener
    ) {
        if (!mlFeatureEnabledSetting.isAgentNameUniquenessEnabled()) {
            listener.onResponse(null);
            return;
        }
        String newName = updateInput.getName();
        if (StringUtils.isBlank(newName) || newName.equals(originalAgent.getName())) {
            // No rename (name omitted/blank), or a no-op rename to the current name.
            listener.onResponse(null);
            return;
        }

        NameUniquenessHelper
            .searchByExactName(client, sdkClient, ML_AGENT_INDEX, newName, updateInput.getTenantId(), ActionListener.wrap(response -> {
                if (response == null) {
                    listener.onResponse(null);
                    return;
                }
                long totalHits = response.getHits().getTotalHits() == null ? 0 : response.getHits().getTotalHits().value();
                if (totalHits > 0) {
                    listener
                        .onFailure(
                            new OpenSearchStatusException(
                                "An agent with name [" + newName + "] already exists. Agent names must be unique.",
                                RestStatus.CONFLICT
                            )
                        );
                } else {
                    listener.onResponse(null);
                }
            }, listener::onFailure));
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}

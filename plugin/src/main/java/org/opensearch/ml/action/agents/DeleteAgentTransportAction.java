/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteAction;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.DeleteDataObjectRequest;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteAgentTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;

    ClusterService clusterService;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public DeleteAgentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLAgentDeleteAction.NAME, transportService, actionFilters, MLAgentDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLAgentDeleteRequest mlAgentDeleteRequest = MLAgentDeleteRequest.fromActionRequest(request);
        String agentId = mlAgentDeleteRequest.getAgentId();
        String tenantId = mlAgentDeleteRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_AGENT_INDEX)
            .id(agentId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                log.debug("Completed Get Agent Request, Agent id:{}", agentId);
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
                        log.info("Failed to get Agent index", cause);
                        wrappedListener.onFailure(new OpenSearchStatusException("Failed to get agent index", RestStatus.NOT_FOUND));
                    } else {
                        log.error("Failed to get ML Agent {}", agentId, cause);
                        wrappedListener.onFailure(cause);
                    }
                } else {
                    try {
                        GetResponse gr = r.getResponse();
                        assert gr != null;
                        if (gr.isExists()) {
                            try (
                                XContentParser parser = jsonXContent
                                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLAgent mlAgent = MLAgent.parse(parser);
                                if (TenantAwareHelper
                                    .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlAgent.getTenantId(), wrappedListener)) {
                                    if (mlAgent.getIsHidden() && !isSuperAdmin) {
                                        wrappedListener
                                            .onFailure(
                                                new OpenSearchStatusException(
                                                    "User doesn't have privilege to perform this operation on this agent",
                                                    RestStatus.FORBIDDEN
                                                )
                                            );
                                    } else {
                                        DeleteRequest deleteRequest = new DeleteRequest(ML_AGENT_INDEX, agentId);
                                        try {
                                            sdkClient
                                                .deleteDataObjectAsync(
                                                    DeleteDataObjectRequest
                                                        .builder()
                                                        .index(deleteRequest.index())
                                                        .id(deleteRequest.id())
                                                        .tenantId(tenantId)
                                                        .build()
                                                )
                                                .whenComplete((response, delThrowable) -> {
                                                    handleDeleteResponse(response, delThrowable, tenantId, wrappedListener);
                                                });
                                        } catch (Exception e) {
                                            log.error("Failed to delete ML agent: {}", agentId, e);
                                            wrappedListener.onFailure(e);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse ml agent {}", agentId, e);
                                wrappedListener.onFailure(e);
                            }
                        } else {
                            wrappedListener.onFailure(new OpenSearchStatusException("Fail to find ml agent", RestStatus.NOT_FOUND));
                        }
                    } catch (Exception e) {
                        log.error("Failed to delete ML agent: {}", agentId, e);
                        wrappedListener.onFailure(e);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Failed to delete ml agent {}", agentId, e);
            actionListener.onFailure(e);
        }
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }

    private void handleDeleteResponse(
        DeleteDataObjectResponse response,
        Throwable throwable,
        String agentId,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            log.error("Failed to delete ML Agent : {}", agentId, cause);
            actionListener.onFailure(cause);
        } else {
            try {
                DeleteResponse deleteResponse = response.deleteResponse();
                log.info("Agent deletion result: {}, agent id: {}", deleteResponse.getResult(), response.id());
                actionListener.onResponse(deleteResponse);
            } catch (Exception e) {
                actionListener.onFailure(e);
            }
        }
    }
}

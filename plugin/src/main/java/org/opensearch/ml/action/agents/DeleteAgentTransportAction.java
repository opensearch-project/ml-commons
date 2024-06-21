/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

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
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
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
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = new GetDataObjectRequest.Builder()
            .index(ML_AGENT_INDEX)
            .id(agentId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {

            sdkClient
                .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    context.restore();
                    log.debug("Completed Get Agent Request, Agent id:{}", agentId);
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        if (cause instanceof IndexNotFoundException) {
                            log.info("Failed to get Agent index", cause);
                            actionListener.onFailure(new OpenSearchStatusException("Failed to get agent index", RestStatus.NOT_FOUND));
                        } else {
                            log.error("Failed to get ML Agent {}", agentId, cause);
                            actionListener.onFailure(cause);
                        }
                    } else {
                        try {
                            GetResponse gr = r.parser() == null ? null : GetResponse.fromXContent(r.parser());
                            if (gr.isExists()) {
                                try (
                                    XContentParser parser = jsonXContent
                                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                                ) {
                                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                    MLAgent mlAgent = MLAgent.parse(parser);
                                    if (TenantAwareHelper
                                        .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlAgent.getTenantId(), actionListener)) {
                                        if (mlAgent.getIsHidden() && !isSuperAdmin) {
                                            actionListener
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
                                                        new DeleteDataObjectRequest.Builder()
                                                            .index(deleteRequest.index())
                                                            .id(deleteRequest.id())
                                                            .tenantId(tenantId)
                                                            .build(),
                                                        client.threadPool().executor(GENERAL_THREAD_POOL)
                                                    )
                                                    .whenComplete(
                                                        (response, delThrowable) -> handleDeleteResponse(
                                                            response,
                                                            delThrowable,
                                                            tenantId,
                                                            actionListener
                                                        )
                                                    );
                                            } catch (Exception e) {
                                                log.error("Failed to delete ML agent: {}", agentId, e);
                                                actionListener.onFailure(e);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to parse ml agent {}", agentId, e);
                                    actionListener.onFailure(e);
                                }
                            } else {
                                actionListener.onFailure(new OpenSearchStatusException("Fail to find ml agent", RestStatus.NOT_FOUND));
                            }
                        } catch (Exception e) {
                            log.error("Failed to delete ML agent: {}", agentId, e);
                            actionListener.onFailure(e);
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
                DeleteResponse deleteResponse = DeleteResponse.fromXContent(response.parser());
                log.info("Agent deletion result: {}, agent id: {}", deleteResponse.getResult(), response.id());
                actionListener.onResponse(deleteResponse);
            } catch (Exception e) {
                actionListener.onFailure(e);
            }
        }
    }
}

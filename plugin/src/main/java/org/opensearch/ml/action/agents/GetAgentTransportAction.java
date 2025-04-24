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
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentGetAction;
import org.opensearch.ml.common.transport.agent.MLAgentGetRequest;
import org.opensearch.ml.common.transport.agent.MLAgentGetResponse;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetAgentTransportAction extends HandledTransportAction<ActionRequest, MLAgentGetResponse> {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;

    ClusterService clusterService;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetAgentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLAgentGetAction.NAME, transportService, actionFilters, MLAgentGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLAgentGetResponse> actionListener) {
        MLAgentGetRequest mlAgentGetRequest = MLAgentGetRequest.fromActionRequest(request);
        String agentId = mlAgentGetRequest.getAgentId();
        String tenantId = mlAgentGetRequest.getTenantId();
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
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                context.restore();
                log.debug("Completed Get Agent Request, Agent id:{}", agentId);
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
                        log.error("Failed to get Agent index", cause);
                        actionListener.onFailure(new OpenSearchStatusException("Failed to get agent index", RestStatus.NOT_FOUND));
                    } else {
                        log.error("Failed to get ML Agent {}", agentId, cause);
                        actionListener.onFailure(cause);
                    }
                } else {
                    try {
                        GetResponse gr = r.parser() == null ? null : GetResponse.fromXContent(r.parser());
                        if (gr != null && gr.isExists()) {
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
                                        actionListener.onResponse(MLAgentGetResponse.builder().mlAgent(mlAgent).build());
                                    }
                                }

                            } catch (Exception e) {
                                log.error("Failed to parse ml agent {}", agentId, e);
                                actionListener.onFailure(e);
                            }
                        } else {
                            actionListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "Failed to find agent with the provided agent id: " + agentId,
                                        RestStatus.NOT_FOUND
                                    )
                                );
                        }
                    } catch (Exception e) {
                        actionListener.onFailure(e);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Failed to get ML agent {}", agentId, e);
            actionListener.onFailure(e);
        }
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}

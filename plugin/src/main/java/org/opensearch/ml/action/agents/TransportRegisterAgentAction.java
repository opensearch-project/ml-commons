/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import java.time.Instant;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportRegisterAgentAction extends HandledTransportAction<ActionRequest, MLRegisterAgentResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLIndicesHandler mlIndicesHandler;
    MLModelManager mlModelManager;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    DiscoveryNodeHelper nodeFilter;
    MLTaskDispatcher mlTaskDispatcher;
    MLStats mlStats;
    ModelAccessControlHelper modelAccessControlHelper;
    ConnectorAccessControlHelper connectorAccessControlHelper;
    MLModelGroupManager mlModelGroupManager;

    @Inject
    public TransportRegisterAgentAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLIndicesHandler mlIndicesHandler,
        MLModelManager mlModelManager,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        Settings settings,
        ThreadPool threadPool,
        Client client,
        DiscoveryNodeHelper nodeFilter,
        MLTaskDispatcher mlTaskDispatcher,
        MLStats mlStats,
        ModelAccessControlHelper modelAccessControlHelper,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLModelGroupManager mlModelGroupManager
    ) {
        super(MLRegisterAgentAction.NAME, transportService, actionFilters, MLRegisterAgentRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlModelManager = mlModelManager;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.nodeFilter = nodeFilter;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlStats = mlStats;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlModelGroupManager = mlModelGroupManager;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLRegisterAgentResponse> listener) {
        User user = RestActionUtils.getUserContext(client);// TODO: check access
        MLRegisterAgentRequest registerAgentRequest = MLRegisterAgentRequest.fromActionRequest(request);
        MLAgent mlAgent = registerAgentRequest.getMlAgent();
        registerAgent(mlAgent, listener);
    }

    private void registerAgent(MLAgent agent, ActionListener<MLRegisterAgentResponse> listener) {
        Instant now = Instant.now();
        MLAgent mlAgent = agent.toBuilder().createdTime(now).lastUpdateTime(now).build();
        mlIndicesHandler.initMLAgentIndex(ActionListener.wrap(result -> {
            if (result) {
                IndexRequest indexRequest = new IndexRequest(ML_AGENT_INDEX);
                XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
                mlAgent.toXContent(builder, ToXContent.EMPTY_PARAMS);
                indexRequest.source(builder);
                client.index(indexRequest, ActionListener.wrap(r -> { listener.onResponse(new MLRegisterAgentResponse(r.getId())); }, e -> {
                    log.error("Failed to index ML agent", e);
                    listener.onFailure(e);
                }));
            } else {
                log.error("Failed to create ML agent index");
            }
        }, e -> {
            log.error("Failed to create ML agent index", e);
            listener.onFailure(e);
        }));
    }

}

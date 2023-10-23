/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;
import static org.opensearch.ml.utils.RestActionUtils.getAlgorithm;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.Client;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.script.ScriptService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMLExecuteAction extends BaseRestHandler {
    private static final String ML_EXECUTE_ACTION = "ml_execute_action";
    private Client client;
    private NamedXContentRegistry xContentRegistry;
    private ScriptService scriptService;
    private ClusterService clusterService;
    private Encryptor encryptor;

    /**
     * Constructor
     */
    public RestMLExecuteAction(
        Client client,
        NamedXContentRegistry xContentRegistry,
        ScriptService scriptService,
        ClusterService clusterService,
        Encryptor encryptor
    ) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.scriptService = scriptService;
        this.clusterService = clusterService;
        this.encryptor = encryptor;
    }

    @Override
    public String getName() {
        return ML_EXECUTE_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/_execute/{%s}", ML_BASE_URI, PARAMETER_ALGORITHM)),
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/agents/{%s}/_execute", ML_BASE_URI, PARAMETER_AGENT_ID))
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLExecuteTaskRequest mlExecuteTaskRequest = getRequest(request);
        if (mlExecuteTaskRequest.getFunctionName() == FunctionName.REMOTE) {
            String connectorId = request.param(PARAMETER_CONNECTOR_ID);
            GetRequest getRequest = new GetRequest(ML_CONNECTOR_INDEX).id(connectorId);

            return channel -> {
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    client.get(getRequest, ActionListener.wrap(r -> {
                        if (r != null && r.isExists()) {
                            try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                Connector connector = Connector.createConnector(parser);

                                connector.decrypt((credential) -> encryptor.decrypt(credential));
                                RemoteConnectorExecutor connectorExecutor = MLEngineClassLoader
                                    .initInstance(connector.getProtocol(), connector, Connector.class);
                                connectorExecutor.setScriptService(scriptService);
                                connectorExecutor.setClusterService(clusterService);
                                connectorExecutor.setClient(client);
                                connectorExecutor.setXContentRegistry(xContentRegistry);

                                ModelTensorOutput modelTensorOutput = connectorExecutor
                                    .executePredict((MLInput) mlExecuteTaskRequest.getInput());
                                MLTaskResponse response = MLTaskResponse.builder().output(modelTensorOutput).build();
                                XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
                                response.toXContent(builder, ToXContent.EMPTY_PARAMS);
                                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                            } catch (Exception e) {
                                e.printStackTrace();
                                channel.sendResponse(new BytesRestResponse(channel, RestStatus.NOT_FOUND, e));
                            }
                        } else {
                            log.error("Failed to get ML connector");
                            try {
                                channel
                                    .sendResponse(
                                        new BytesRestResponse(
                                            channel,
                                            RestStatus.NOT_FOUND,
                                            new ResourceNotFoundException("ML connector not found")
                                        )
                                    );
                            } catch (IOException ex) {
                                log.error("Failed to send error response", ex);
                            }
                        }
                    }, e -> {
                        log.error("Failed to get ML connector", e);
                        try {
                            channel.sendResponse(new BytesRestResponse(channel, RestStatus.NOT_FOUND, e));
                        } catch (IOException ex) {
                            log.error("Failed to send error response", ex);
                        }
                    }));
                }

            };
        } else {
            return channel -> client.execute(MLExecuteTaskAction.INSTANCE, mlExecuteTaskRequest, new RestToXContentListener<>(channel));
        }
    }

    /**
     * Creates a MLExecuteTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLExecuteTaskRequest
     */
    @VisibleForTesting
    MLExecuteTaskRequest getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        String uri = request.getHttpRequest().uri();
        FunctionName functionName = null;
        Input input = null;
        if (uri.startsWith(ML_BASE_URI + "/agents/")) {
            String agentId = request.param(PARAMETER_AGENT_ID);
            functionName = FunctionName.AGENT;
            input = MLInput.parse(parser, functionName.name());
            ((AgentMLInput) input).setAgentId(agentId);
        } else {
            String algorithm = getAlgorithm(request).toUpperCase(Locale.ROOT);
            functionName = FunctionName.from(algorithm);
            input = parser.namedObject(Input.class, functionName.name(), null);
        }

        return new MLExecuteTaskRequest(functionName, input);
    }
}

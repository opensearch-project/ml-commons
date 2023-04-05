/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.remote;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.connector.APISchema;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorState;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportCreateConnectorAction extends HandledTransportAction<ActionRequest, MLCreateConnectorResponse> {
    private final TransportService transportService;
    private final ClusterService clusterService;
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;

    @Inject
    public TransportCreateConnectorAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        MLIndicesHandler mlIndicesHandler,
        Client client
    ) {
        super(MLCreateConnectorAction.NAME, transportService, actionFilters, MLCreateConnectorRequest::new);
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreateConnectorResponse> listener) {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest.fromActionRequest(request);
        MLCreateConnectorInput mlCreateConnectorInput = mlCreateConnectorRequest.getMlCreateConnectorInput();
        if (mlCreateConnectorInput.getConnectorAPIs() == null) {
            throw new IllegalArgumentException("Invalid Connector template, APIs are missing");
        }
        String connectorName = mlCreateConnectorInput.getParameters().get("connector_name");

        try {
            resolveAPIPlaceholders(mlCreateConnectorInput.getConnectorAPIs().getPredictSchema(), mlCreateConnectorInput.getParameters());
            resolveAPIPlaceholders(mlCreateConnectorInput.getConnectorAPIs().getMetadataSchema(), mlCreateConnectorInput.getParameters());

            Instant now = Instant.now();

            Connector connector = Connector
                .builder()
                .name(connectorName)
                .version(mlCreateConnectorInput.getParameters().get("version"))
                .description(mlCreateConnectorInput.getParameters().get("description"))
                .predictSchema(mlCreateConnectorInput.getConnectorAPIs().getPredictSchema())
                .metadataSchema(mlCreateConnectorInput.getConnectorAPIs().getMetadataSchema())
                .connectorState(ConnectorState.CREATED)
                .createdTime(now)
                .lastUpdateTime(now)
                .build();
            mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(indexCreated -> {
                if (!indexCreated) {
                    listener.onFailure(new RuntimeException("No response to create ML Connector index"));
                    return;
                }

                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    ActionListener<IndexResponse> indexResponseListener = ActionListener.wrap(r -> {
                        log.info("Connector saved into index, result:{}, connector id: {}", r.getResult(), r.getId());
                        MLCreateConnectorResponse response = new MLCreateConnectorResponse(r.getId(), ConnectorState.CREATED.name());
                        listener.onResponse(response);
                    }, e -> { listener.onFailure(e); });

                    IndexRequest indexRequest = new IndexRequest(ML_CONNECTOR_INDEX);
                    indexRequest
                        .source(connector.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                    client.index(indexRequest, ActionListener.runBefore(indexResponseListener, () -> context.restore()));
                } catch (Exception e) {
                    log.error("Failed to save ML model", e);
                    listener.onFailure(e);
                }
            }, e -> {
                log.error("Failed to init ML model index", e);
                listener.onFailure(e);
            }));

        } catch (IllegalArgumentException illegalArgumentException) {
            log.error("Failed to create connector " + connectorName, illegalArgumentException);
            listener.onFailure(illegalArgumentException);
        } catch (Exception e) {
            // todo need to specify what exception
            log.error("Failed to create connector " + connectorName, e);
            listener.onFailure(e);
        }
    }

    private void resolveAPIPlaceholders(APISchema apiSchema, Map<String, String> parameters) {
        apiSchema.setMethod(replacePlaceholders(apiSchema.getMethod(), parameters));
        apiSchema.setUrl(replacePlaceholders(apiSchema.getUrl(), parameters));
        apiSchema.setHeaders(replacePlaceholders(apiSchema.getHeaders(), parameters));
        apiSchema.setRequestBody(replacePlaceholders(apiSchema.getRequestBody(), parameters));
    }

    private String replacePlaceholders(String content, Map<String, String> parameters) {
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = parameters.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Cannot resolve parameter " + key + " in the connector template");
            }
            matcher.appendReplacement(sb, value);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}

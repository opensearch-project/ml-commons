/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.remote;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.connector.template.DetachedConnector.CONNECTOR_PROTOCOL_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.toJson;

import java.time.Instant;

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
import org.opensearch.ml.common.connector.template.ConnectorState;
import org.opensearch.ml.common.connector.template.DetachedConnector;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportCreateConnectorAction extends HandledTransportAction<ActionRequest, MLCreateConnectorResponse> {
    public static final String CONNECTOR_NAME_FIELD = "connector_name";
    public static final String CONNECTOR_DESCRIPTION_FIELD = "description";
    public static final String CONNECTOR_VERSION_FIELD = "version";

    private final TransportService transportService;
    private final ClusterService clusterService;
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final MLEngine mlEngine;

    @Inject
    public TransportCreateConnectorAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        MLIndicesHandler mlIndicesHandler,
        Client client,
        MLEngine mlEngine
    ) {
        super(MLCreateConnectorAction.NAME, transportService, actionFilters, MLCreateConnectorRequest::new);
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.mlEngine = mlEngine;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreateConnectorResponse> listener) {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest.fromActionRequest(request);
        MLCreateConnectorInput mlCreateConnectorInput = mlCreateConnectorRequest.getMlCreateConnectorInput();
        if (mlCreateConnectorInput.getConnectorTemplate() == null) {
            throw new IllegalArgumentException("Invalid Connector template, APIs are missing");
        }
        String connectorName = mlCreateConnectorInput.getMetadata().get(CONNECTOR_NAME_FIELD);

        try {
            Instant now = Instant.now();
            DetachedConnector connector = DetachedConnector
                .builder()
                .name(connectorName)
                .version(mlCreateConnectorInput.getMetadata().get(CONNECTOR_VERSION_FIELD))
                .description(mlCreateConnectorInput.getMetadata().get(CONNECTOR_DESCRIPTION_FIELD))
                .protocol(mlCreateConnectorInput.getMetadata().get(CONNECTOR_PROTOCOL_FIELD))
                .parameterStr(toJson(mlCreateConnectorInput.getParameters()))
                .credentialStr(toJson(mlCreateConnectorInput.getCredential()))
                .predictAPI(mlCreateConnectorInput.getConnectorTemplate().getPredictSchema().toString())
                .metadataAPI(mlCreateConnectorInput.getConnectorTemplate().getMetadataSchema().toString())
                .connectorState(ConnectorState.CREATED)
                .createdTime(now)
                .lastUpdateTime(now)
                .build();
            connector.encrypt((credential) -> mlEngine.encrypt(credential));
            log.info("connector created, indexing into the connector system index");
            mlIndicesHandler.initMLConnectorIndex(ActionListener.wrap(indexCreated -> {
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
                    log.error("Failed to save ML connector", e);
                    listener.onFailure(e);
                }
            }, e -> {
                log.error("Failed to init ML connector index", e);
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
}

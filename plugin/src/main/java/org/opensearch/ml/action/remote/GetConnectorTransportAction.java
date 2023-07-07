/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.remote;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.connector.MLConnectorGetAction;
import org.opensearch.ml.common.transport.connector.MLConnectorGetRequest;
import org.opensearch.ml.common.transport.connector.MLConnectorGetResponse;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetConnectorTransportAction extends HandledTransportAction<ActionRequest, MLConnectorGetResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;

    ConnectorAccessControlHelper connectorAccessControlHelper;

    @Inject
    public GetConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper
    ) {
        super(MLConnectorGetAction.NAME, transportService, actionFilters, MLConnectorGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLConnectorGetResponse> actionListener) {
        MLConnectorGetRequest mlConnectorGetRequest = MLConnectorGetRequest.fromActionRequest(request);
        String connectorId = mlConnectorGetRequest.getConnectorId();
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlConnectorGetRequest.isReturnContent());
        GetRequest getRequest = new GetRequest(ML_CONNECTOR_INDEX).id(connectorId).fetchSourceContext(fetchSourceContext);
        User user = RestActionUtils.getUserContext(client);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.runBefore(ActionListener.wrap(r -> {
                log.debug("Completed Get Connector Request, id:{}", connectorId);

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        Connector mlConnector = Connector.createConnector(parser);
                        mlConnector.removeCredential();
                        if (connectorAccessControlHelper.hasPermission(user, mlConnector)) {
                            actionListener.onResponse(MLConnectorGetResponse.builder().mlConnector(mlConnector).build());
                        } else {
                            actionListener.onFailure(new MLValidationException("You don't have permission to access this connector"));
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse ml connector" + r.getId(), e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener.onFailure(new IllegalArgumentException("Failed to find connector with the provided connector id: " + connectorId));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find connector"));
                } else {
                    log.error("Failed to get ML connector " + connectorId, e);
                    actionListener.onFailure(e);
                }
            }), context::restore));
        } catch (Exception e) {
            log.error("Failed to get ML connector " + connectorId, e);
            actionListener.onFailure(e);
        }

    }
}

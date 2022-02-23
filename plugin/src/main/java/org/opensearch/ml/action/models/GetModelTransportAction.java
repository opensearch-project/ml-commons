/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.indices.MLIndicesHandler.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.parameter.MLModel;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetModelTransportAction extends HandledTransportAction<ActionRequest, MLModelGetResponse> {

    TransportService transportService;
    Client client;
    NamedXContentRegistry xContentRegistry;

    @Inject
    public GetModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLModelGetAction.NAME, transportService, actionFilters, MLModelGetRequest::new);
        this.transportService = transportService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLModelGetResponse> actionListener) {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.fromActionRequest(request);
        String modelId = mlModelGetRequest.getModelId();
        GetRequest getRequest = new GetRequest(ML_MODEL_INDEX).id(modelId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.wrap(r -> {
                log.info("Completed Get Model Request, id:{}", modelId);

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLModel mlModel = MLModel.parse(parser);
                        actionListener.onResponse(MLModelGetResponse.builder().mlModel(mlModel).build());
                    } catch (Exception e) {
                        log.error("Failed to parse ml model" + r.getId(), e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find model " + modelId));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find model " + modelId));
                } else {
                    log.error("Failed to get ML model " + modelId, e);
                    actionListener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            log.error("Failed to get ML model " + modelId, e);
            actionListener.onFailure(e);
        }

    }
}

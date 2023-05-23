/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_VALIDATE_BACKEND_ROLES;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.SecurityUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetModelTransportAction extends HandledTransportAction<ActionRequest, MLModelGetResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    private volatile boolean filterByEnabled;

    @Inject
    public GetModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        Settings settings,
        ClusterService clusterService
    ) {
        super(MLModelGetAction.NAME, transportService, actionFilters, MLModelGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        filterByEnabled = ML_COMMONS_VALIDATE_BACKEND_ROLES.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_VALIDATE_BACKEND_ROLES, it -> filterByEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLModelGetResponse> actionListener) {
        MLModelGetRequest mlModelGetRequest = MLModelGetRequest.fromActionRequest(request);
        String modelId = mlModelGetRequest.getModelId();
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlModelGetRequest.isReturnContent());
        GetRequest getRequest = new GetRequest(ML_MODEL_INDEX).id(modelId).fetchSourceContext(fetchSourceContext);
        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.runBefore(ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        GetResponse getResponse = r;
                        String algorithmName = getResponse.getSource().get(ALGORITHM_FIELD).toString();

                        MLModel mlModel = MLModel.parse(parser, algorithmName);
                        SecurityUtils.validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(access -> {
                            if ((filterByEnabled) && (!access)) {
                                actionListener
                                    .onFailure(new MLValidationException("User Doesn't have previlege to perform this operation"));
                            } else {
                                log.debug("Completed Get Model Request, id:{}", modelId);
                                actionListener.onResponse(MLModelGetResponse.builder().mlModel(mlModel).build());
                            }
                        }, e -> {
                            log.error("Failed to validate Access for Model Id " + modelId, e);
                            actionListener.onFailure(e);
                        }));

                    } catch (Exception e) {
                        log.error("Failed to parse ml model" + r.getId(), e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener.onFailure(new IllegalArgumentException("Failed to find model with the provided model id: " + modelId));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find model"));
                } else {
                    log.error("Failed to get ML model " + modelId, e);
                    actionListener.onFailure(e);
                }
            }), () -> context.restore()));
        } catch (Exception e) {
            log.error("Failed to get ML model " + modelId, e);
            actionListener.onFailure(e);
        }

    }
}

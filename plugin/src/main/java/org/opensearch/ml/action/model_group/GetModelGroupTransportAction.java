/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetRequest;
import org.opensearch.ml.common.transport.model_group.MLModelGroupGetResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetModelGroupTransportAction extends HandledTransportAction<ActionRequest, MLModelGroupGetResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public GetModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLModelGroupGetAction.NAME, transportService, actionFilters, MLModelGroupGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLModelGroupGetResponse> actionListener) {
        MLModelGroupGetRequest mlModelGroupGetRequest = MLModelGroupGetRequest.fromActionRequest(request);
        String modelGroupId = mlModelGroupGetRequest.getModelGroupId();
        GetRequest getRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);
        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModelGroupGetResponse> wrappedListener = ActionListener.runBefore(actionListener, () -> context.restore());
            client.get(getRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

                        MLModelGroup mlModelGroup = MLModelGroup.parse(parser);
                        modelAccessControlHelper.validateModelGroupAccess(user, modelGroupId, client, ActionListener.wrap(access -> {
                            if (!access) {
                                wrappedListener
                                    .onFailure(
                                        new OpenSearchStatusException(
                                            "User doesn't have privilege to perform this operation on this model group",
                                            RestStatus.FORBIDDEN
                                        )
                                    );
                            } else {
                                wrappedListener.onResponse(MLModelGroupGetResponse.builder().mlModelGroup(mlModelGroup).build());
                            }
                        }, e -> {
                            log.error("Failed to validate access for Model Group " + modelGroupId, e);
                            wrappedListener.onFailure(e);
                        }));

                    } catch (Exception e) {
                        log.error("Failed to parse ml model group" + r.getId(), e);
                        wrappedListener.onFailure(e);
                    }
                } else {
                    wrappedListener
                        .onFailure(
                            new OpenSearchStatusException(
                                "Failed to find model group with the provided model group id: " + modelGroupId,
                                RestStatus.NOT_FOUND
                            )
                        );
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group index"));
                } else {
                    log.error("Failed to get ML model group" + modelGroupId, e);
                    wrappedListener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            log.error("Failed to get ML model group " + modelGroupId, e);
            actionListener.onFailure(e);
        }

    }
}

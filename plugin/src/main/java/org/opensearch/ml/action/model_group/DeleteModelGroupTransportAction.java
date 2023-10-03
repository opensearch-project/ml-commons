/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_GROUP_ID;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteModelGroupTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public DeleteModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLModelGroupDeleteAction.NAME, transportService, actionFilters, MLModelGroupDeleteRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelGroupDeleteRequest mlModelGroupDeleteRequest = MLModelGroupDeleteRequest.fromActionRequest(request);
        String modelGroupId = mlModelGroupDeleteRequest.getModelGroupId();
        DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_GROUP_INDEX, modelGroupId);
        User user = RestActionUtils.getUserContext(client);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, () -> context.restore());
            modelAccessControlHelper.validateModelGroupAccess(user, modelGroupId, client, ActionListener.wrap(access -> {
                if (!access) {
                    wrappedListener.onFailure(new MLValidationException("User doesn't have privilege to delete this model group"));
                } else {
                    BoolQueryBuilder query = new BoolQueryBuilder();
                    query.filter(new TermQueryBuilder(PARAMETER_MODEL_GROUP_ID, modelGroupId));

                    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);
                    SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX).source(searchSourceBuilder);
                    client.search(searchRequest, ActionListener.wrap(mlModels -> {
                        if (mlModels == null || mlModels.getHits().getTotalHits() == null || mlModels.getHits().getTotalHits().value == 0) {
                            client.delete(deleteRequest, new ActionListener<DeleteResponse>() {
                                @Override
                                public void onResponse(DeleteResponse deleteResponse) {
                                    log.debug("Completed Delete Model Group Request, task id:{} deleted", modelGroupId);
                                    wrappedListener.onResponse(deleteResponse);
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    log.error("Failed to delete ML Model Group " + modelGroupId, e);
                                    wrappedListener.onFailure(e);
                                }
                            });
                        } else {
                            throw new MLValidationException("Cannot delete the model group when it has associated model versions");
                        }

                    }, e -> {
                        if (e instanceof IndexNotFoundException) {
                            wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                        } else {
                            log.error("Failed to search models with the specified Model Group Id " + modelGroupId, e);
                            wrappedListener.onFailure(e);
                        }
                    }));
                }
            }, e -> {
                log.error("Failed to validate Access for Model Group " + modelGroupId, e);
                wrappedListener.onFailure(e);
            }));
        }
    }
}

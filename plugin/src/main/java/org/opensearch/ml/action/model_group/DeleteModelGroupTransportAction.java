/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_GROUP_ID;

import java.io.IOException;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
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
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteModelGroupTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;

    final ModelAccessControlHelper modelAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public DeleteModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLModelGroupDeleteAction.NAME, transportService, actionFilters, MLModelGroupDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelGroupDeleteRequest mlModelGroupDeleteRequest = MLModelGroupDeleteRequest.fromActionRequest(request);
        String modelGroupId = mlModelGroupDeleteRequest.getModelGroupId();
        String tenantId = mlModelGroupDeleteRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_GROUP_INDEX, modelGroupId);
        User user = RestActionUtils.getUserContext(client);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            modelAccessControlHelper.validateModelGroupAccess(user, modelGroupId, client, ActionListener.wrap(access -> {
                if (!access) {
                    wrappedListener.onFailure(new MLValidationException("User doesn't have privilege to delete this model group"));
                } else {
                    BoolQueryBuilder query = new BoolQueryBuilder();
                    query.filter(new TermQueryBuilder(PARAMETER_MODEL_GROUP_ID, modelGroupId));

                    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);
                    SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX).source(searchSourceBuilder);

                    SearchDataObjectRequest searchDataObjectRequest = new SearchDataObjectRequest.Builder()
                        .indices(ML_MODEL_INDEX)
                        .searchSourceBuilder(searchSourceBuilder)
                        .build();

                    sdkClient
                        .searchDataObjectAsync(searchDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                        .whenComplete((sr, st) -> {
                            if (sr != null) {
                                try {
                                    SearchResponse searchResponse = SearchResponse.fromXContent(sr.parser());
                                    SearchHit[] searchHits = searchResponse.getHits().getHits();
                                    if (searchHits.length == 0) {
                                        deleteModelGroup(deleteRequest, tenantId, wrappedListener);
                                    } else {
                                        actionListener
                                            .onFailure(
                                                new OpenSearchStatusException(
                                                    "Cannot delete the model group when it has associated model versions",
                                                    RestStatus.CONFLICT
                                                )
                                            );
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to parse search response", e);
                                    actionListener
                                        .onFailure(
                                            new OpenSearchStatusException(
                                                "Failed to parse search response",
                                                RestStatus.INTERNAL_SERVER_ERROR
                                            )
                                        );
                                }
                            } else {
                                Exception cause = SdkClientUtils.unwrapAndConvertToException(st);
                                handleModelSearchFailure(modelGroupId, tenantId, cause, actionListener);
                            }
                        });

                }
            }, e -> {
                log.error("Failed to validate Access for Model Group {}", modelGroupId, e);
                wrappedListener.onFailure(e);
            }));
        }
    }

    private void deleteModelGroup(DeleteRequest deleteRequest, String tenantId, ActionListener<DeleteResponse> actionListener) {
        try {
            sdkClient
                .deleteDataObjectAsync(
                    new DeleteDataObjectRequest.Builder().index(deleteRequest.index()).id(deleteRequest.id()).tenantId(tenantId).build(),
                    client.threadPool().executor(GENERAL_THREAD_POOL)
                )
                .whenComplete((response, throwable) -> handleDeleteResponse(response, throwable, deleteRequest.id(), actionListener));
        } catch (Exception e) {
            log.error("Failed to delete Model group : {}", deleteRequest.id(), e);
            actionListener.onFailure(e);
        }
    }

    private void handleDeleteResponse(
        DeleteDataObjectResponse response,
        Throwable throwable,
        String modelGroupId,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            log.error("Failed to delete ML Model Group {}", modelGroupId, cause);
            actionListener.onFailure(cause);
        } else {
            try {
                DeleteResponse deleteResponse = DeleteResponse.fromXContent(response.parser());
                log.debug("Completed Delete Model Group Request, model group id:{} deleted", response.id());
                actionListener.onResponse(deleteResponse);
            } catch (IOException e) {
                actionListener.onFailure(e);
            }
        }
    }

    private void handleModelSearchFailure(
        String modelGroupId,
        String tenantId,
        Exception cause,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (cause instanceof IndexNotFoundException) {
            DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_GROUP_INDEX, modelGroupId);
            deleteModelGroup(deleteRequest, tenantId, actionListener);
            return;
        }

        log.error("Failed to search for models using model group id: {}", modelGroupId, cause);
        actionListener.onFailure(cause);
    }
}

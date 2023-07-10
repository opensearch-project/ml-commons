/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.common.MLModel.MODEL_ID_FIELD;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
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
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteModelTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    static final String TIMEOUT_MSG = "Timeout while deleting model of ";
    static final String BULK_FAILURE_MSG = "Bulk failure while deleting model of ";
    static final String SEARCH_FAILURE_MSG = "Search failure while deleting model of ";
    static final String OS_STATUS_EXCEPTION_MESSAGE = "Failed to delete all model chunks";
    Client client;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public DeleteModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLModelDeleteAction.NAME, transportService, actionFilters, MLModelDeleteRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.fromActionRequest(request);
        String modelId = mlModelDeleteRequest.getModelId();
        MLModelGetRequest mlModelGetRequest = new MLModelGetRequest(modelId, false);
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlModelGetRequest.isReturnContent());
        GetRequest getRequest = new GetRequest(ML_MODEL_INDEX).id(modelId).fetchSourceContext(fetchSourceContext);
        User user = RestActionUtils.getUserContext(client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        GetResponse getResponse = r;
                        String algorithmName = "";
                        if (getResponse.getSource() != null && getResponse.getSource().get(ALGORITHM_FIELD) != null) {
                            algorithmName = getResponse.getSource().get(ALGORITHM_FIELD).toString();
                        }
                        MLModel mlModel = MLModel.parse(parser, algorithmName);

                        modelAccessControlHelper
                            .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(access -> {
                                if (!access) {
                                    actionListener
                                        .onFailure(
                                            new MLValidationException("User doesn't have privilege to perform this operation on this model")
                                        );
                                } else {
                                    MLModelState mlModelState = mlModel.getModelState();
                                    if (mlModelState.equals(MLModelState.LOADED)
                                        || mlModelState.equals(MLModelState.LOADING)
                                        || mlModelState.equals(MLModelState.PARTIALLY_LOADED)
                                        || mlModelState.equals(MLModelState.DEPLOYED)
                                        || mlModelState.equals(MLModelState.DEPLOYING)
                                        || mlModelState.equals(MLModelState.PARTIALLY_DEPLOYED)) {
                                        actionListener
                                            .onFailure(
                                                new Exception(
                                                    "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete"
                                                )
                                            );
                                    } else if (StringUtils.isNotEmpty(mlModel.getModelGroupId())) {
                                        searchModel(mlModel.getModelGroupId(), ActionListener.wrap(response -> {
                                            boolean isLastModelOfGroup = false;
                                            if (response != null
                                                && response.getHits() != null
                                                && response.getHits().getTotalHits() != null
                                                && response.getHits().getTotalHits().value == 1) {
                                                isLastModelOfGroup = true;
                                            }
                                            deleteModel(modelId, mlModel.getModelGroupId(), isLastModelOfGroup, actionListener);
                                        }, e -> {
                                            log.error("Failed to Search Model index " + modelId, e);
                                            actionListener.onFailure(e);
                                        }));
                                    } else {
                                        deleteModel(modelId, mlModel.getModelGroupId(), false, actionListener);
                                    }
                                }
                            }, e -> {
                                log.error("Failed to validate Access for Model Id " + modelId, e);
                                actionListener.onFailure(e);
                            }));
                    } catch (Exception e) {
                        log.error("Failed to parse ml model " + r.getId(), e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener
                        .onFailure(new IllegalArgumentException("Failed to find model to delete with the provided model id: " + modelId));
                }
            }, e -> { actionListener.onFailure(new MLResourceNotFoundException("Fail to find model")); }));
        } catch (Exception e) {
            log.error("Failed to delete ML model " + modelId, e);
            actionListener.onFailure(e);
        }
    }

    private void searchModel(String modelGroupId, ActionListener<SearchResponse> listener) {
        BoolQueryBuilder query = new BoolQueryBuilder();
        query.filter(new TermQueryBuilder(MLModel.MODEL_GROUP_ID_FIELD, modelGroupId));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);
        SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX).source(searchSourceBuilder);
        client.search(searchRequest, ActionListener.wrap(response -> { listener.onResponse(response); }, e -> {
            log.error("Failed to search Model index", e);
            listener.onFailure(e);
        }));
    }

    @VisibleForTesting
    void deleteModelChunks(String modelId, DeleteResponse deleteResponse, ActionListener<DeleteResponse> actionListener) {
        DeleteByQueryRequest deleteModelsRequest = new DeleteByQueryRequest(ML_MODEL_INDEX);
        deleteModelsRequest.setQuery(new TermsQueryBuilder(MODEL_ID_FIELD, modelId));

        client.execute(DeleteByQueryAction.INSTANCE, deleteModelsRequest, ActionListener.wrap(r -> {
            if ((r.getBulkFailures() == null || r.getBulkFailures().size() == 0)
                && (r.getSearchFailures() == null || r.getSearchFailures().size() == 0)) {
                log.debug("All model chunks are deleted for model {}", modelId);
                if (deleteResponse != null) {
                    // If model metaData not found and deleteResponse is null, do not return here.
                    // ResourceNotFound is returned to notify that this model was deleted.
                    // This is a walk around to avoid cleaning up model leftovers. Will revisit if
                    // necessary.
                    actionListener.onResponse(deleteResponse);
                }
            } else {
                returnFailure(r, modelId, actionListener);
            }
        }, e -> {
            log.error("Failed to delete ML model for " + modelId, e);
            actionListener.onFailure(e);
        }));
    }

    private void returnFailure(BulkByScrollResponse response, String modelId, ActionListener<DeleteResponse> actionListener) {
        String errorMessage = "";
        if (response.isTimedOut()) {
            errorMessage = OS_STATUS_EXCEPTION_MESSAGE + ", " + TIMEOUT_MSG + modelId;
        } else if (!response.getBulkFailures().isEmpty()) {
            errorMessage = OS_STATUS_EXCEPTION_MESSAGE + ", " + BULK_FAILURE_MSG + modelId;
        } else {
            errorMessage = OS_STATUS_EXCEPTION_MESSAGE + ", " + SEARCH_FAILURE_MSG + modelId;
        }
        log.debug(response.toString());
        actionListener.onFailure(new OpenSearchStatusException(errorMessage, RestStatus.INTERNAL_SERVER_ERROR));
    }

    private void deleteModel(
        String modelId,
        String modelGroupId,
        boolean isLastModelOfGroup,
        ActionListener<DeleteResponse> actionListener
    ) {
        DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_INDEX, modelId);
        client.delete(deleteRequest, new ActionListener<DeleteResponse>() {
            @Override
            public void onResponse(DeleteResponse deleteResponse) {
                if (isLastModelOfGroup) {
                    deleteModelGroup(modelGroupId);
                }
                deleteModelChunks(modelId, deleteResponse, actionListener);
            }

            @Override
            public void onFailure(Exception e) {
                log.error("Failed to delete model meta data for model: " + modelId, e);
                if (e instanceof ResourceNotFoundException) {
                    deleteModelChunks(modelId, null, actionListener);
                }
                actionListener.onFailure(e);
            }
        });
    }

    private void deleteModelGroup(String modelGroupId) {
        DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_GROUP_INDEX, modelGroupId);
        client.delete(deleteRequest, new ActionListener<DeleteResponse>() {
            @Override
            public void onResponse(DeleteResponse deleteResponse) {
                log.debug("Completed Delete Model Group for modelGroupId:{}", modelGroupId);
            }

            @Override
            public void onFailure(Exception e) {
                log.error("Failed to delete ML Model Group with Id:{} " + modelGroupId, e);
            }
        });
    }
}

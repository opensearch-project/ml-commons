/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.ml.common.CommonValue.ML_CONTROLLER_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.MODEL_ID_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.RetryableAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
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

    MLModelManager mlModelManager;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public DeleteModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLModelManager mlModelManager
    ) {
        super(MLModelDeleteAction.NAME, transportService, actionFilters, MLModelDeleteRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlModelManager = mlModelManager;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.fromActionRequest(request);
        String modelId = mlModelDeleteRequest.getModelId();
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, () -> context.restore());
            mlModelManager.getModel(modelId, null, excludes, ActionListener.wrap(mlModel -> {
                if (mlModel != null) {
                    Boolean isHidden = mlModel.getIsHidden();
                    if (isHidden != null && isHidden) {
                        if (!isSuperAdmin) {
                            wrappedListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "User doesn't have privilege to perform this operation on this model",
                                        RestStatus.FORBIDDEN
                                    )
                                );
                        } else {
                            retryDeleteModel(modelId, isHidden, mlModelDeleteRequest, wrappedListener);
                        }
                    } else {
                        modelAccessControlHelper
                            .validateModelGroupAccess(user, mlModel.getModelGroupId(), client, ActionListener.wrap(access -> {
                                if (!access) {
                                    wrappedListener
                                        .onFailure(
                                            new OpenSearchStatusException(
                                                "User doesn't have privilege to perform this operation on this model",
                                                RestStatus.FORBIDDEN
                                            )
                                        );
                                } else {
                                    retryDeleteModel(modelId, isHidden, mlModelDeleteRequest, wrappedListener);
                                }
                            }, e -> {
                                log.error(getErrorMessage("Failed to validate Access", modelId, isHidden), e);
                                wrappedListener.onFailure(e);
                            }));
                    }
                } else {
                    // when model metadata is not found, model chunk and controller might still there, delete them here and return success
                    // response
                    deleteModelChunksAndController(wrappedListener, modelId, false, null);
                }
            }, e -> { wrappedListener.onFailure((new OpenSearchStatusException("Failed to find model", RestStatus.NOT_FOUND))); }));
        } catch (Exception e) {
            log.error("Failed to delete ML model " + modelId, e);
            actionListener.onFailure(e);
        }
    }

    @VisibleForTesting
    void retryDeleteModel(
        String modelId,
        Boolean isHidden,
        MLModelDeleteRequest mlModelDeleteRequest,
        ActionListener<DeleteResponse> actionListener
    ) {
        Boolean isRetry = mlModelDeleteRequest.getRetry() == null || mlModelDeleteRequest.getRetry();
        TimeValue retryDelay = mlModelDeleteRequest.getRetryDelay();
        TimeValue retryTimeout = mlModelDeleteRequest.getRetryTimeout();

        final RetryableAction<DeleteResponse> deleteModelAction = new RetryableAction<>(
            log,
            client.threadPool(),
            retryDelay,
            retryTimeout,
            actionListener
        ) {

            Integer retryTimes = 0;

            @Override
            public void tryAction(ActionListener<DeleteResponse> actionListener) {
                mlModelManager.getModelState(modelId, ActionListener.wrap(mlModelState -> {
                    // the listener here is RetryingListener
                    // If the request success, or can not retry, will call delegate listener
                    if (isModelNotDeployed(mlModelState)) {
                        deleteModel(modelId, isHidden, actionListener);
                    } else {
                        log.error("Model delete failed due to model is in deploying or deployed state for modelId {}", modelId);
                        actionListener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
                                    RestStatus.CONFLICT
                                )
                            );
                    }
                }, e -> {
                    log.error(getErrorMessage("Failed to get model state", modelId, isHidden), e);
                    actionListener.onFailure(e);
                }));
            }

            @Override
            public boolean shouldRetry(Exception e) {
                Throwable cause = ExceptionsHelper.unwrapCause(e);
                Integer maxRetry = mlModelDeleteRequest.getMaxRetry();
                boolean shouldRetry = cause instanceof OpenSearchStatusException
                    && ((OpenSearchStatusException) cause).status() == RestStatus.CONFLICT
                    && isRetry;
                if (shouldRetry) {
                    retryTimes++;
                    log.debug(String.format(Locale.ROOT, "The %d-th retry for model deletion", retryTimes));
                    if (retryTimes > maxRetry) {
                        shouldRetry = false;
                    }
                }
                return shouldRetry;
            }
        };
        deleteModelAction.run();
    };

    @VisibleForTesting
    void deleteModelChunks(String modelId, Boolean isHidden, ActionListener<Boolean> actionListener) {
        DeleteByQueryRequest deleteModelsRequest = new DeleteByQueryRequest(ML_MODEL_INDEX);
        deleteModelsRequest.setQuery(new TermsQueryBuilder(MODEL_ID_FIELD, modelId));

        client.execute(DeleteByQueryAction.INSTANCE, deleteModelsRequest, ActionListener.wrap(r -> {
            if ((r.getBulkFailures() == null || r.getBulkFailures().size() == 0)
                && (r.getSearchFailures() == null || r.getSearchFailures().size() == 0)) {
                log.debug(getErrorMessage("All model chunks are deleted for the provided model.", modelId, isHidden));
                actionListener.onResponse(true);
            } else {
                returnFailure(r, modelId, actionListener);
            }
        }, e -> {
            log.error(getErrorMessage("Failed to delete model chunks for the provided model", modelId, isHidden), e);
            actionListener.onFailure(e);
        }));
    }

    private void returnFailure(BulkByScrollResponse response, String modelId, ActionListener<Boolean> actionListener) {
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

    private void deleteModel(String modelId, Boolean isHidden, ActionListener<DeleteResponse> actionListener) {
        DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_INDEX, modelId).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.delete(deleteRequest, new ActionListener<>() {
            @Override
            public void onResponse(DeleteResponse deleteResponse) {
                deleteModelChunksAndController(actionListener, modelId, isHidden, deleteResponse);
            }

            @Override
            public void onFailure(Exception e) {
                if (e instanceof ResourceNotFoundException) {
                    deleteModelChunksAndController(actionListener, modelId, isHidden, null);
                } else {
                    log.error(getErrorMessage("Model is not all cleaned up, please try again.", modelId, isHidden), e);
                    actionListener.onFailure(e);
                }
            }
        });
    }

    private void deleteModelChunksAndController(
        ActionListener<DeleteResponse> actionListener,
        String modelId,
        Boolean isHidden,
        DeleteResponse deleteResponse
    ) {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicBoolean bothDeleted = new AtomicBoolean(true);
        ActionListener<Boolean> countDownActionListener = ActionListener.wrap(b -> {
            countDownLatch.countDown();
            bothDeleted.compareAndSet(true, b);
            if (countDownLatch.getCount() == 0) {
                if (bothDeleted.get()) {
                    log
                        .debug(
                            getErrorMessage(
                                "model chunks and model controller for the provided model deleted successfully",
                                modelId,
                                isHidden
                            )
                        );
                    if (deleteResponse != null) {
                        actionListener.onResponse(deleteResponse);
                    } else {
                        actionListener.onFailure(new OpenSearchStatusException("Failed to find model", RestStatus.NOT_FOUND));
                    }
                } else {
                    actionListener
                        .onFailure(
                            new IllegalStateException(getErrorMessage("Model is not all cleaned up, please try again.", modelId, isHidden))
                        );
                }
            }
        }, e -> {
            countDownLatch.countDown();
            bothDeleted.compareAndSet(true, false);
            if (countDownLatch.getCount() == 0) {
                actionListener
                    .onFailure(
                        new IllegalStateException(getErrorMessage("Model is not all cleaned up, please try again.", modelId, isHidden), e)
                    );
            }
        });
        deleteModelChunks(modelId, isHidden, countDownActionListener);
        deleteController(modelId, isHidden, countDownActionListener);
    }

    /**
     * Delete the model controller for a model after the model is deleted from the
     * ML index.
     *
     * @param modelId model ID
     */
    private void deleteController(String modelId, Boolean isHidden, ActionListener<Boolean> actionListener) {
        DeleteRequest deleteRequest = new DeleteRequest(ML_CONTROLLER_INDEX, modelId);
        client.delete(deleteRequest, new ActionListener<>() {
            @Override
            public void onResponse(DeleteResponse deleteResponse) {
                log
                    .info(
                        getErrorMessage(
                            "Model controller for the provided model successfully deleted from index, result: {}.",
                            modelId,
                            isHidden
                        ),
                        deleteResponse.getResult()
                    );
                actionListener.onResponse(true);
            }

            @Override
            public void onFailure(Exception e) {
                if (e instanceof ResourceNotFoundException) {
                    log
                        .info(
                            getErrorMessage(
                                "Model controller not deleted due to no model controller found for the given model.",
                                modelId,
                                isHidden
                            )
                        );
                    actionListener.onResponse(true); // we consider this as success
                } else {
                    log.error(getErrorMessage("Failed to delete model controller for the given model.", modelId, isHidden), e);
                    actionListener.onFailure(e);
                }
            }
        });
    }

    private Boolean isModelNotDeployed(MLModelState mlModelState) {
        return !mlModelState.equals(MLModelState.LOADED)
            && !mlModelState.equals(MLModelState.LOADING)
            && !mlModelState.equals(MLModelState.PARTIALLY_LOADED)
            && !mlModelState.equals(MLModelState.DEPLOYED)
            && !mlModelState.equals(MLModelState.DEPLOYING)
            && !mlModelState.equals(MLModelState.PARTIALLY_DEPLOYED);
    }

    // this method is only to stub static method.
    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}

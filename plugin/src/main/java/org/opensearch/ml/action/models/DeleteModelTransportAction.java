/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONTROLLER_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.common.MLModel.IS_HIDDEN_FIELD;
import static org.opensearch.ml.common.MLModel.MODEL_ID_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
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
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    Settings settings;

    ModelAccessControlHelper modelAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public DeleteModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        Settings settings,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLModelDeleteAction.NAME, transportService, actionFilters, MLModelDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.fromActionRequest(request);
        String modelId = mlModelDeleteRequest.getModelId();
        String tenantId = mlModelDeleteRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        MLModelGetRequest mlModelGetRequest = new MLModelGetRequest(modelId, false, false, tenantId);
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlModelGetRequest.isReturnContent());
        GetDataObjectRequest getDataObjectRequest = new GetDataObjectRequest.Builder()
            .index(ML_MODEL_INDEX)
            .id(modelId)
            .fetchSourceContext(fetchSourceContext)
            .build();
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, () -> context.restore());
            sdkClient
                .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    if (throwable == null) {
                        if (r != null && r.parser().isPresent()) {
                            try (XContentParser parser = r.parser().get()) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                String algorithmName = "";
                                Map<String, Object> source = r.source();
                                if (source != null && source.get(ALGORITHM_FIELD) != null) {
                                    algorithmName = source.get(ALGORITHM_FIELD).toString();
                                }
                                MLModel mlModel = MLModel.parse(parser, algorithmName);
                                if (!TenantAwareHelper
                                    .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlModel.getTenantId(), actionListener)) {
                                    return;
                                }
                                Boolean isHidden = (Boolean) r.source().get(IS_HIDDEN_FIELD);
                                MLModelState mlModelState = mlModel.getModelState();
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
                                        if (isModelNotDeployed(mlModelState)) {
                                            deleteModel(modelId, isHidden, actionListener);
                                        } else {
                                            wrappedListener
                                                .onFailure(
                                                    new OpenSearchStatusException(
                                                        "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
                                                        RestStatus.BAD_REQUEST
                                                    )
                                                );
                                        }
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
                                            } else if (isModelNotDeployed(mlModelState)) {
                                                deleteModel(modelId, isHidden, actionListener);
                                            } else {
                                                wrappedListener
                                                    .onFailure(
                                                        new OpenSearchStatusException(
                                                            "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete",
                                                            RestStatus.BAD_REQUEST
                                                        )
                                                    );
                                            }
                                        }, e -> {
                                            log.error(getErrorMessage("Failed to validate Access", modelId, isHidden), e);
                                            wrappedListener.onFailure(e);
                                        }));
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse ml model " + r.id(), e);
                                wrappedListener.onFailure(e);
                            }
                        } else {
                            // when model metadata is not found, model chunk and controller might still there, delete them here and return
                            // success
                            // response
                            deleteModelChunksAndController(wrappedListener, modelId, false, null);
                        }
                    } else {
                        wrappedListener.onFailure((new OpenSearchStatusException("Failed to find model", RestStatus.NOT_FOUND)));
                    }
                });
        } catch (Exception e) {
            log.error("Failed to delete ML model " + modelId, e);
            actionListener.onFailure(e);
        }
    }

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
        DeleteDataObjectRequest deleteDataObjectRequest = new DeleteDataObjectRequest.Builder().index(ML_MODEL_INDEX).id(modelId).build();
        sdkClient
            .deleteDataObjectAsync(deleteDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
            .whenComplete((r, throwable) -> {
                if (throwable == null) {
                    try {
                        DeleteResponse deleteResponse = DeleteResponse.fromXContent(r.parser());
                        deleteModelChunksAndController(actionListener, modelId, isHidden, deleteResponse);
                    } catch (Exception e) {
                        actionListener.onFailure(e);
                    }
                } else {
                    Exception e = SdkClientUtils.unwrapAndConvertToException(throwable);
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
        // TODO: this uses DeleteByQuery which isn't on SdkClient
        // evaluate if it's safe to leave as is
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
        DeleteDataObjectRequest deleteDataObjectRequest = new DeleteDataObjectRequest.Builder()
            .index(ML_CONTROLLER_INDEX)
            .id(modelId)
            .build();
        sdkClient
            .deleteDataObjectAsync(deleteDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
            .whenComplete((r, throwable) -> {
                if (throwable == null) {
                    try {
                        DeleteResponse deleteResponse = DeleteResponse.fromXContent(r.parser());
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
                    } catch (Exception e) {
                        actionListener.onFailure(e);
                    }
                } else {
                    Exception e = SdkClientUtils.unwrapAndConvertToException(throwable);
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

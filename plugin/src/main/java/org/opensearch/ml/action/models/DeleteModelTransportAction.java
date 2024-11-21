/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONTROLLER_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.MLModel.ALGORITHM_FIELD;
import static org.opensearch.ml.common.MLModel.IS_HIDDEN_FIELD;
import static org.opensearch.ml.common.MLModel.MODEL_ID_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.getErrorMessage;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.ingest.GetPipelineAction;
import org.opensearch.action.ingest.GetPipelineRequest;
import org.opensearch.action.search.GetSearchPipelineAction;
import org.opensearch.action.search.GetSearchPipelineRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
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
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.engine.tools.AgentModelsSearcher;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.SearchHit;
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
    static final String PIPELINE_TARGET_MODEL_KEY = "model_id";
    Client client;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    Settings settings;

    ModelAccessControlHelper modelAccessControlHelper;

    final AgentModelsSearcher agentModelsSearcher;

    @Inject
    public DeleteModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        Settings settings,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        AgentModelsSearcher agentModelsSearcher
    ) {
        super(MLModelDeleteAction.NAME, transportService, actionFilters, MLModelDeleteRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.agentModelsSearcher = agentModelsSearcher;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelDeleteRequest mlModelDeleteRequest = MLModelDeleteRequest.fromActionRequest(request);
        String modelId = mlModelDeleteRequest.getModelId();
        MLModelGetRequest mlModelGetRequest = new MLModelGetRequest(modelId, false, false);
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlModelGetRequest.isReturnContent());
        GetRequest getRequest = new GetRequest(ML_MODEL_INDEX).id(modelId).fetchSourceContext(fetchSourceContext);
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, () -> context.restore());
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
                        Boolean isHidden = (Boolean) r.getSource().get(IS_HIDDEN_FIELD);
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
                                    checkDownstreamTaskBeforeDeleteModel(modelId, isHidden, actionListener);
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
                                        checkDownstreamTaskBeforeDeleteModel(modelId, isHidden, actionListener);
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
                        log.error("Failed to parse ml model " + r.getId(), e);
                        wrappedListener.onFailure(e);
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
    void deleteModelChunks(String modelId, Boolean isHidden, ActionListener<Boolean> actionListener) {
        DeleteByQueryRequest deleteModelsRequest = new DeleteByQueryRequest(ML_MODEL_INDEX);
        deleteModelsRequest.setQuery(new TermsQueryBuilder(MODEL_ID_FIELD, modelId)).setRefresh(true);

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

    private void checkAgentBeforeDeleteModel(String modelId, ActionListener<Boolean> actionListener) {
        // check whether agent are using them
        SearchRequest searchAgentRequest = agentModelsSearcher.constructQueryRequest(modelId);
        client.search(searchAgentRequest, ActionListener.wrap(searchResponse -> {
            SearchHit[] searchHits = searchResponse.getHits().getHits();
            if (searchHits.length == 0) {
                actionListener.onResponse(true);
            } else {
                String errorMessage = formatAgentErrorMessage(searchHits);

                actionListener.onFailure(new OpenSearchStatusException(errorMessage, RestStatus.CONFLICT));
            }

        }, e -> {
            if (e instanceof IndexNotFoundException) {
                actionListener.onResponse(true);
                return;
            }
            log.error("Failed to delete ML Model: " + modelId, e);
            actionListener.onFailure(e);

        }));
    }

    private void checkIngestPipelineBeforeDeleteModel(String modelId, ActionListener<Boolean> actionListener) {
        GetPipelineRequest getPipelineRequest = new GetPipelineRequest();
        client.execute(GetPipelineAction.INSTANCE, getPipelineRequest, ActionListener.wrap(ingestPipelineResponse -> {
            List<String> allDependentPipelineIds = findDependentPipelines(
                ingestPipelineResponse.pipelines(),
                modelId,
                org.opensearch.ingest.PipelineConfiguration::getConfigAsMap,
                org.opensearch.ingest.PipelineConfiguration::getId
            );
            if (allDependentPipelineIds.isEmpty()) {
                actionListener.onResponse(true);
            } else {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            String
                                .format(
                                    Locale.ROOT,
                                    "%d ingest pipelines are still using this model, please delete or update the pipelines first: %s",
                                    allDependentPipelineIds.size(),
                                    Arrays.toString(allDependentPipelineIds.toArray(new String[0]))
                                ),
                            RestStatus.CONFLICT
                        )
                    );
            }
        }, e -> {
            log.error("Failed to delete ML Model: " + modelId, e);
            actionListener.onFailure(e);

        }));

    }

    private void checkSearchPipelineBeforeDeleteModel(String modelId, ActionListener<Boolean> actionListener) {
        GetSearchPipelineRequest getSearchPipelineRequest = new GetSearchPipelineRequest();
        client.execute(GetSearchPipelineAction.INSTANCE, getSearchPipelineRequest, ActionListener.wrap(searchPipelineResponse -> {
            List<String> allDependentPipelineIds = findDependentPipelines(
                searchPipelineResponse.pipelines(),
                modelId,
                org.opensearch.search.pipeline.PipelineConfiguration::getConfigAsMap,
                org.opensearch.search.pipeline.PipelineConfiguration::getId
            );
            if (allDependentPipelineIds.isEmpty()) {
                actionListener.onResponse(true);
            } else {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            String
                                .format(
                                    Locale.ROOT,
                                    "%d search pipelines are still using this model, please delete or update the pipelines first: %s",
                                    allDependentPipelineIds.size(),
                                    Arrays.toString(allDependentPipelineIds.toArray(new String[0]))
                                ),
                            RestStatus.CONFLICT
                        )
                    );
            }
        }, e -> {
            log.error("Failed to delete ML Model: " + modelId, e);
            actionListener.onFailure(e);

        }));

    }

    private void checkDownstreamTaskBeforeDeleteModel(String modelId, Boolean isHidden, ActionListener<DeleteResponse> actionListener) {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicBoolean noneBlocked = new AtomicBoolean(true);
        CopyOnWriteArrayList<String> errorMessages = new CopyOnWriteArrayList<>();
        ActionListener<Boolean> countDownActionListener = ActionListener.wrap(b -> {
            countDownLatch.countDown();
            noneBlocked.compareAndSet(true, b);
            if (countDownLatch.getCount() == 0) {
                if (noneBlocked.get()) {
                    deleteModel(modelId, isHidden, actionListener);
                } else {
                    actionListener.onFailure(new OpenSearchStatusException(String.join(". ", errorMessages), RestStatus.CONFLICT));
                }
            }
        }, e -> {
            countDownLatch.countDown();
            noneBlocked.compareAndSet(true, false);
            errorMessages.add(e.getMessage());
            if (countDownLatch.getCount() == 0) {
                actionListener.onFailure(new OpenSearchStatusException(String.join(". ", errorMessages), RestStatus.CONFLICT));
            }

        });
        checkAgentBeforeDeleteModel(modelId, countDownActionListener);
        checkIngestPipelineBeforeDeleteModel(modelId, countDownActionListener);
        checkSearchPipelineBeforeDeleteModel(modelId, countDownActionListener);
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
        DeleteRequest deleteRequest = new DeleteRequest(ML_CONTROLLER_INDEX, modelId).setRefreshPolicy(IMMEDIATE);
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

    private <T> List<String> findDependentPipelines(
        List<T> pipelineConfigurations,
        String candidateModelId,
        Function<T, Map<String, Object>> getConfigFunction,
        Function<T, String> getIdFunction
    ) {
        List<String> dependentPipelineConfigurations = new ArrayList<>();
        for (T pipelineConfiguration : pipelineConfigurations) {
            Map<String, Object> config = getConfigFunction.apply(pipelineConfiguration);
            if (searchThroughConfig(config, candidateModelId)) {
                dependentPipelineConfigurations.add(getIdFunction.apply(pipelineConfiguration));
            }
        }
        return dependentPipelineConfigurations;
    }

    // This method is to go through the pipeline configs and he configuration is a map of string to objects.
    // Objects can be a list or a map. we will search exhaustively through the configuration for any match of the candidateId.
    private Boolean searchThroughConfig(Object searchCandidate, String candidateId) {
        // Use a stack to store the elements to be processed
        Deque<Pair<String, Object>> stack = new ArrayDeque<>();
        stack.push(Pair.of("", searchCandidate));

        while (!stack.isEmpty()) {
            // Pop an item from the stack
            Pair<String, Object> current = stack.pop();
            String currentKey = current.getLeft();
            Object currentCandidate = current.getRight();

            if (currentCandidate instanceof String) {
                // Check for a match
                if (Objects.equals(currentKey, PIPELINE_TARGET_MODEL_KEY) && Objects.equals(candidateId, currentCandidate)) {
                    return true;
                }
            } else if (currentCandidate instanceof List<?>) {
                // Push all elements in the list onto the stack
                for (Object v : (List<?>) currentCandidate) {
                    stack.push(Pair.of(currentKey, v));
                }
            } else if (currentCandidate instanceof Map<?, ?>) {
                // Push all values in the map onto the stack
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) currentCandidate).entrySet()) {
                    String key = (String) entry.getKey();
                    Object value = entry.getValue();
                    stack.push(Pair.of(key, value));
                }
            }
        }

        // If no match is found
        return false;
    }

    private String formatAgentErrorMessage(SearchHit[] hits) {
        boolean isHidden = false;
        for (SearchHit hit : hits) {
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
            isHidden = isHidden || Boolean.parseBoolean((String) sourceAsMap.getOrDefault(MLAgent.IS_HIDDEN_FIELD, false));
        }
        if (isHidden) {
            return String
                .format(Locale.ROOT, "%d agents are still using this model, please delete or update the agents first", hits.length);
        }
        List<String> agentIds = new ArrayList<>();
        for (SearchHit hit : hits) {
            agentIds.add(hit.getId());
        }
        return String
            .format(
                Locale.ROOT,
                "%d agents are still using this model, please delete or update the agents first: %s",
                hits.length,
                Arrays.toString(agentIds.toArray(new String[0]))
            );
    }

    // this method is only to stub static method.
    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}

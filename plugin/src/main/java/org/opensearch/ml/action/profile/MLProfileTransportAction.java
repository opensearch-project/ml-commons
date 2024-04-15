/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.env.Environment;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.search.SearchHit;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLProfileTransportAction extends
    TransportNodesAction<MLProfileRequest, MLProfileResponse, MLProfileNodeRequest, MLProfileNodeResponse> {
    private MLTaskManager mlTaskManager;
    private final JvmService jvmService;
    private final MLModelManager mlModelManager;

    private final Client client;

    /**
     * Constructor
     * @param threadPool ThreadPool to use
     * @param clusterService ClusterService
     * @param transportService TransportService
     * @param actionFilters Action Filters
     * @param mlTaskManager mlTaskCache object
     * @param environment OpenSearch Environment
     * @param mlModelManager ML model manager
     */
    @Inject
    public MLProfileTransportAction(
        ThreadPool threadPool,
        ClusterService clusterService,
        TransportService transportService,
        ActionFilters actionFilters,
        MLTaskManager mlTaskManager,
        Environment environment,
        MLModelManager mlModelManager,
        Client client
    ) {
        super(
            MLProfileAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLProfileRequest::new,
            MLProfileNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLProfileNodeResponse.class
        );
        this.mlTaskManager = mlTaskManager;
        this.jvmService = new JvmService(environment.settings());
        this.mlModelManager = mlModelManager;
        this.client = client;
    }

    @Override
    protected MLProfileResponse newResponse(
        MLProfileRequest request,
        List<MLProfileNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLProfileResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLProfileNodeRequest newNodeRequest(MLProfileRequest request) {
        return new MLProfileNodeRequest(request);
    }

    @Override
    protected MLProfileNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLProfileNodeResponse(in);
    }

    @Override
    protected MLProfileNodeResponse nodeOperation(MLProfileNodeRequest request) {
        return createMLProfileNodeResponse(request.getMlProfileRequest());
    }

    private MLProfileNodeResponse createMLProfileNodeResponse(MLProfileRequest mlProfileRequest) {
        log.debug("Calculating ml profile response on node id:{}", clusterService.localNode().getId());
        Map<String, MLTask> mlLocalTasks = new HashMap<>();
        Map<String, MLModelProfile> mlLocalModels = new HashMap<>();
        MLProfileInput mlProfileInput = mlProfileRequest.getMlProfileInput();
        Set<String> targetModelIds = mlProfileInput.getModelIds();

        CountDownLatch latch = new CountDownLatch(1);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        searchHiddenModels(ActionListener.wrap(hiddenModels -> {
            Arrays.stream(mlTaskManager.getAllTaskIds()).forEach(taskId -> {
                MLTask mlTask = mlTaskManager.getMLTask(taskId);
                if (isSuperAdmin || !hiddenModels.contains(mlTask.getModelId())) {
                    if (mlProfileInput.isReturnAllTasks()
                        || (!mlProfileInput.emptyTasks() && mlProfileInput.getTaskIds().contains(taskId))) {
                        log.debug("Runtime task profile is found for model {}", mlTask.getModelId());
                        mlLocalTasks.put(taskId, mlTask);
                    }
                    if (mlProfileInput.isReturnAllTasks()
                        || (!mlProfileInput.emptyModels() && targetModelIds.contains(mlTask.getModelId()))) {
                        log.debug("Runtime task profile is found for model {}", mlTask.getModelId());
                        mlLocalTasks.put(taskId, mlTask);
                    }
                }
            });
            Arrays.stream(mlModelManager.getAllModelIds()).forEach(modelId -> {
                if (isSuperAdmin || !hiddenModels.contains(modelId)) {
                    if (mlProfileInput.isReturnAllModels() || (!mlProfileInput.emptyModels() && targetModelIds.contains(modelId))) {
                        log.debug("Runtime model profile is found for model {}", modelId);
                        MLModelProfile modelProfile = mlModelManager.getModelProfile(modelId);
                        if (modelProfile != null) {
                            if (isSuperAdmin && hiddenModels.contains(modelId)) {
                                modelProfile.setIsHidden(Boolean.TRUE);
                            }
                            mlLocalModels.put(modelId, modelProfile);
                        }
                    }
                }
            });
        }, e -> { log.error("Search Hidden model wasn't successful"); }), latch);

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Handle interruption if necessary
            Thread.currentThread().interrupt();
        }

        return new MLProfileNodeResponse(clusterService.localNode(), mlLocalTasks, mlLocalModels);
    }

    @VisibleForTesting
    void searchHiddenModels(ActionListener<Set<String>> listener, CountDownLatch latch) {
        SearchRequest searchRequest = buildHiddenModelSearchRequest();
        // Use a try-with-resources block to ensure resources are properly released
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            // Wrap the listener to restore thread context before calling it
            ActionListener<Set<String>> internalListener = ActionListener.runAfter(listener, () -> {
                latch.countDown();
                threadContext.restore();
            });
            // Wrap the search response handler to handle success and failure cases
            // Notify the listener of any search failures
            ActionListener<SearchResponse> al = ActionListener.wrap(response -> {
                // Initialize the result set
                Set<String> result = new HashSet<>(response.getHits().getHits().length); // Set initial capacity to the number of hits

                // Iterate over the search hits and add their IDs to the result set
                for (SearchHit hit : response.getHits()) {
                    result.add(hit.getId());
                }
                // Notify the listener of the search results
                internalListener.onResponse(result);
            }, internalListener::onFailure);

            // Execute the search request asynchronously
            client.search(searchRequest, al);
        } catch (Exception e) {
            // Notify the listener of any unexpected errors
            listener.onFailure(e);
        }
    }

    private SearchRequest buildHiddenModelSearchRequest() {
        SearchRequest searchRequest = new SearchRequest(CommonValue.ML_MODEL_INDEX);
        // Build the query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder
            .filter(
                QueryBuilders
                    .boolQuery()
                    .must(QueryBuilders.termQuery(MLModel.IS_HIDDEN_FIELD, true))
                    // Add the additional filter to exclude documents where "chunk_number" exists
                    .mustNot(QueryBuilders.existsQuery("chunk_number"))
            );
        searchRequest.source().query(boolQueryBuilder);
        // Specify the fields to include in the search results (only the "_id" field)
        // No fields to exclude
        searchRequest.source().fetchSource(new String[] { "_id" }, new String[] {});
        return searchRequest;
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}

/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.autoredeploy;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.client.OpenSearchClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLCommonsSettings;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLModelAutoReDeployer {

    private final ClusterService clusterService;
    private final Client client;
    private final Settings settings;
    private boolean enableAutoReDeployModel;
    private boolean onlyRunOnMlNode;
    private int autoDeployMaxRetryTimes;
    private boolean allowCustomDeploymentPlan;

    private final MLModelManager mlModelManager;
    private final Queue<ModelAutoRedeployArrangement> modelAutoRedeployArrangements = new ConcurrentLinkedQueue<>();

    private final SearchRequestBuilderFactory searchRequestBuilderFactory;

    @Setter
    private ActionListener<Boolean> startCronJobListener;

    public MLModelAutoReDeployer(
        ClusterService clusterService,
        Client client,
        Settings settings,
        MLModelManager mlModelManager,
        SearchRequestBuilderFactory searchRequestBuilderFactory
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.settings = settings;
        this.mlModelManager = mlModelManager;
        this.searchRequestBuilderFactory = searchRequestBuilderFactory;

        enableAutoReDeployModel = MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.get(settings);
        onlyRunOnMlNode = MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE.get(settings);
        autoDeployMaxRetryTimes = MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.get(settings);
        allowCustomDeploymentPlan = MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.get(settings);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE, it -> enableAutoReDeployModel = it);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE, undeployModelsOnDataNodesConsumer());

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(
                MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES,
                it -> autoDeployMaxRetryTimes = it
            );

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN, it -> allowCustomDeploymentPlan = it);
    }

    private void undeployModelsOnDataNodes() {
        List<String> dataNodeIds = new ArrayList<>();
        clusterService.state().nodes().getDataNodes().values().iterator().forEachRemaining(x -> { dataNodeIds.add(x.getId()); });
        if (dataNodeIds.size() > 0)
            triggerUndeployModelsOnDataNodes(dataNodeIds);
    }

    @VisibleForTesting
    Consumer<Boolean> undeployModelsOnDataNodesConsumer() {
        return x -> {
            onlyRunOnMlNode = x;
            if (onlyRunOnMlNode) {
                undeployModelsOnDataNodes();
            }
        };
    }

    public void buildAutoReloadArrangement(List<String> addedNodes, String clusterManagerNodeId) {
        if (!enableAutoReDeployModel) {
            log.info("Model auto reload configuration is false, not performing auto reloading!");
            startCronjobAndClearListener();
            return;
        }
        String localNodeId = clusterService.localNode().getId();
        if (Strings.isNullOrEmpty(localNodeId) || !localNodeId.equals(clusterManagerNodeId)) {
            log
                .info(
                    "model auto reloading should be initialized by cluster manager node only, current node id is empty or current node not cluster manager!"
                );
            return;
        }
        triggerAutoDeployModels(addedNodes);
    }

    public void redeployAModel() {
        if (!enableAutoReDeployModel) {
            log.info("Model auto reload configuration is false, not performing auto reloading!");
            startCronjobAndClearListener();
            return;
        }
        if (modelAutoRedeployArrangements.size() == 0) {
            log.info("No models needs to be auto redeployed!");
            startCronjobAndClearListener();
            return;
        }
        ModelAutoRedeployArrangement modelAutoRedeployArrangement = modelAutoRedeployArrangements.poll();
        triggerModelRedeploy(modelAutoRedeployArrangement);
    }

    private void triggerAutoDeployModels(List<String> addedNodes) {
        ActionListener<SearchResponse> listener = ActionListener.wrap(res -> {
            if (res != null && res.getHits() != null && res.getHits().getTotalHits() != null && res.getHits().getTotalHits().value > 0) {
                Arrays
                    .stream(res.getHits().getHits())
                    .filter(
                        x -> x != null
                            && x.getSourceAsMap() != null
                            && (Integer) Optional
                                .ofNullable(x.getSourceAsMap().get(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD))
                                .orElse(0) < autoDeployMaxRetryTimes
                    )
                    .forEach(x -> {
                        ModelAutoRedeployArrangement modelAutoRedeployArrangement = ModelAutoRedeployArrangement
                            .builder()
                            .addedNodes(addedNodes)
                            .searchResponse(x)
                            .build();
                        boolean notExist = modelAutoRedeployArrangements.stream().noneMatch(y -> y.equals(modelAutoRedeployArrangement));
                        if (notExist)
                            modelAutoRedeployArrangements.add(modelAutoRedeployArrangement);
                    });
                redeployAModel();
            }
        }, e -> {
            log.error("Failed to query need auto redeploy models, no action will be performed, addedNodes are: {}", addedNodes, e);
            startCronjobAndClearListener();
        });

        queryRunningModels(listener);
    }

    private void triggerUndeployModelsOnDataNodes(List<String> dataNodeIds) {
        List<String> modelIds = new ArrayList<>();
        ActionListener<SearchResponse> listener = ActionListener.wrap(res -> {
            if (res != null && res.getHits() != null && res.getHits().getTotalHits() != null && res.getHits().getTotalHits().value > 0) {
                Arrays.stream(res.getHits().getHits()).forEach(x -> modelIds.add(x.getId()));
                if (modelIds.size() > 0) {
                    ActionListener<MLUndeployModelNodesResponse> undeployModelListener = ActionListener.wrap(r -> {
                        log.info("Undeploy models on data nodes successfully!");
                    }, e -> { log.error("Failed to undeploy models on data nodes, error is: {}", e.getMessage(), e); });
                    MLUndeployModelNodesRequest undeployModelNodesRequest = new MLUndeployModelNodesRequest(
                        dataNodeIds.toArray(new String[0]),
                        modelIds.toArray(new String[0])
                    );
                    client.execute(MLUndeployModelAction.INSTANCE, undeployModelNodesRequest, undeployModelListener);
                }
            }
        }, e -> { log.error("Failed to query need undeploy models, no action will be performed"); });
        queryRunningModels(listener);
    }

    private void queryRunningModels(ActionListener<SearchResponse> listener) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        TermsQueryBuilder builder = new TermsQueryBuilder(
            MLModel.MODEL_STATE_FIELD,
            Arrays
                .asList(
                    MLModelState.LOADING.name(),
                    MLModelState.PARTIALLY_LOADED.name(),
                    MLModelState.LOADED.name(),
                    MLModelState.DEPLOYING.name(),
                    MLModelState.PARTIALLY_DEPLOYED.name(),
                    MLModelState.DEPLOYED.name()
                )
        );

        FieldSortBuilder sortBuilder = SortBuilders.fieldSort(MLModel.LAST_DEPLOYED_TIME_FIELD).order(SortOrder.ASC);

        String[] includes = new String[] {
            MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD,
            MLModel.PLANNING_WORKER_NODES_FIELD,
            MLModel.DEPLOY_TO_ALL_NODES_FIELD };

        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        FetchSourceContext fetchContext = new FetchSourceContext(true, includes, excludes);
        searchSourceBuilder.query(builder).sort(sortBuilder).fetchSource(fetchContext);
        SearchRequestBuilder searchRequestBuilder = searchRequestBuilderFactory
            .getSearchRequestBuilder(client, SearchAction.INSTANCE)
            .setIndices(ML_MODEL_INDEX)
            .setSource(searchSourceBuilder)
            .setSize(10_000);

        searchRequestBuilder.execute(listener);
    }

    @SuppressWarnings("unchecked")
    private void triggerModelRedeploy(ModelAutoRedeployArrangement modelAutoRedeployArrangement) {
        String modelId = modelAutoRedeployArrangement.getSearchResponse().getId();
        List<String> addedNodes = modelAutoRedeployArrangement.getAddedNodes();
        List<String> planningWorkerNodes = (List<String>) modelAutoRedeployArrangement
            .getSearchResponse()
            .getSourceAsMap()
            .get(MLModel.PLANNING_WORKER_NODES_FIELD);
        Integer autoRedeployRetryTimes = (Integer) modelAutoRedeployArrangement
            .getSearchResponse()
            .getSourceAsMap()
            .get(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD);
        Boolean deployToAllNodes = (Boolean) Optional
            .ofNullable(modelAutoRedeployArrangement.getSearchResponse().getSourceAsMap().get(MLModel.DEPLOY_TO_ALL_NODES_FIELD))
            .orElse(false);
        // calculate node ids.
        String[] nodeIds = null;
        if (deployToAllNodes || !allowCustomDeploymentPlan) {
            nodeIds = new String[0];
        } else if (planningWorkerNodes != null && planningWorkerNodes.size() > 0) {
            // allow custom deploy plan and not deploy to all case, we need to check if the added nodes in planning worker nodes.
            List<String> needRedeployPlanningWorkerNodes = Arrays
                .stream(planningWorkerNodes.toArray(new String[0]))
                .filter(addedNodes::contains)
                .collect(Collectors.toList());
            nodeIds = needRedeployPlanningWorkerNodes.size() > 0 ? planningWorkerNodes.toArray(new String[0]) : null;
        }

        if (nodeIds == null) {
            log
                .info(
                    "Allow custom deployment plan is true and deploy to all nodes is false and added nodes are not in planning worker nodes list, not to auto redeploy the model to the new nodes!"
                );
            return;
        }

        ActionListener<MLDeployModelResponse> listener = ActionListener.wrap(res -> {
            log.info("Triggered model auto redeploy, task id is: {}, task status is: {}", res.getTaskId(), res.getStatus());
        }, e -> {
            log
                .error(
                    "Exception occurred when auto redeploying the model, model id is: {}, exception is: {}, skipping current model auto redeploy and starting next model redeploy!",
                    modelId,
                    e.getMessage(),
                    e
                );
            redeployAModel();
        });

        mlModelManager
            .updateModel(
                modelId,
                ImmutableMap.of(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD, Optional.ofNullable(autoRedeployRetryTimes).orElse(0) + 1)
            );

        MLDeployModelRequest deployModelRequest = new MLDeployModelRequest(modelId, nodeIds, false, true);
        client.execute(MLDeployModelAction.INSTANCE, deployModelRequest, listener);
    }

    private void startCronjobAndClearListener() {
        boolean managerNode = clusterService.localNode().isClusterManagerNode();
        if (managerNode && startCronJobListener != null) {
            startCronJobListener.onResponse(true);
            startCronJobListener = null;
        }
    }

    @Data
    @Builder
    static class ModelAutoRedeployArrangement {
        private List<String> addedNodes;
        private SearchHit searchResponse;
    }

    public static class SearchRequestBuilderFactory {
        public SearchRequestBuilder getSearchRequestBuilder(OpenSearchClient client, SearchAction action) {
            return new SearchRequestBuilder(client, action);
        }
    }
}

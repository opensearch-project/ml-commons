/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.autoredeploy;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.block.ClusterBlockException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.OpenSearchClient;

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
    private final MLTaskManager mlTaskManager;
    private final Queue<ModelAutoRedeployArrangement> modelAutoRedeployArrangements = new ConcurrentLinkedQueue<>();

    /**
     * An in-flight redeploy whose deploy request was never answered (so its task id is unknown) is treated as finished
     * after this long, so a lost response can never block a model's auto redeploy forever.
     */
    static final long IN_FLIGHT_REDEPLOY_STALE_AFTER_MS = TimeUnit.MINUTES.toMillis(10);

    /**
     * Auto redeploys currently running, by model id. Node joins usually come in bursts (scale-out, blue/green), and every
     * join used to start another cluster-wide deploy task of the same model. Those tasks collide on the nodes that are
     * still loading the model ("Duplicate deploy model task"), end below the success ratio, and each one consumes one
     * of the model's lifetime auto redeploy retries, so after a few joins the model was excluded from auto redeploy for
     * good. Instead, joins that arrive while a redeploy of the model is running are coalesced into one follow-up
     * redeploy that starts when the running one finishes.
     */
    private final Map<String, InFlightRedeploy> inFlightRedeploys = new ConcurrentHashMap<>();

    /**
     * How long a retry count this node wrote, or that a finished redeploy implies, is trusted over the model document. The
     * document is updated asynchronously and without a refresh, so a search shortly after a redeploy can return an old
     * count; in particular the reset after a successful redeploy lands after the redeploy has already been released.
     */
    static final long KNOWN_RETRY_TIMES_VALID_MS = TimeUnit.SECONDS.toMillis(30);

    private final Map<String, long[]> knownRetryTimes = new ConcurrentHashMap<>();

    private final SearchRequestBuilderFactory searchRequestBuilderFactory;

    @Setter
    private volatile ActionListener<Boolean> startCronJobListener;

    private volatile long lastQueueProgressMillis;

    @VisibleForTesting
    LongSupplier currentTimeMillis = System::currentTimeMillis;

    public MLModelAutoReDeployer(
        ClusterService clusterService,
        Client client,
        Settings settings,
        MLModelManager mlModelManager,
        SearchRequestBuilderFactory searchRequestBuilderFactory
    ) {
        this(clusterService, client, settings, mlModelManager, null, searchRequestBuilderFactory);
    }

    public MLModelAutoReDeployer(
        ClusterService clusterService,
        Client client,
        Settings settings,
        MLModelManager mlModelManager,
        MLTaskManager mlTaskManager,
        SearchRequestBuilderFactory searchRequestBuilderFactory
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.settings = settings;
        this.mlModelManager = mlModelManager;
        this.mlTaskManager = mlTaskManager;
        this.searchRequestBuilderFactory = searchRequestBuilderFactory;
        if (mlTaskManager != null) {
            // every way a deploy task ends on its coordinating node (all nodes replied, nodes left, failed, timed out)
            // removes it from the task cache
            mlTaskManager.addTaskRemovedListener(task -> {
                if (task.getTaskType() == MLTaskType.DEPLOY_MODEL) {
                    onDeployTaskCompleted(task.getModelId(), task.getTaskId());
                }
            });
        }

        enableAutoReDeployModel = MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.get(settings);
        onlyRunOnMlNode = MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE.get(settings);
        autoDeployMaxRetryTimes = MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.get(settings);
        allowCustomDeploymentPlan = MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.get(settings);

        clusterService.getClusterSettings().addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE, it -> {
            enableAutoReDeployModel = it;
            if (!it) {
                // nothing will take these from the queue any more
                modelAutoRedeployArrangements.clear();
            }
        });

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
        if (!dataNodeIds.isEmpty())
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
        triggerAutoDeployModels(addedNodes, null, null);
    }

    public void redeployAModel() {
        if (!enableAutoReDeployModel) {
            log.info("Model auto reload configuration is false, not performing auto reloading!");
            startCronjobAndClearListener();
            return;
        }
        if (modelAutoRedeployArrangements.isEmpty()) {
            log.info("No models needs to be auto redeployed!");
            startCronjobAndClearListener();
            return;
        }
        if (!isElectedClusterManager()) {
            // the new cluster manager redeploys the models itself; draining this queue would run a second deploy of each
            log.info("Not continuing model auto redeploy: this node is no longer the cluster manager");
            modelAutoRedeployArrangements.clear();
            knownRetryTimes.clear();
            return;
        }
        ModelAutoRedeployArrangement modelAutoRedeployArrangement = modelAutoRedeployArrangements.poll();
        lastQueueProgressMillis = currentTimeMillis.getAsLong();
        triggerModelRedeploy(modelAutoRedeployArrangement);
    }

    /**
     * @param retryTimesOverride for a follow-up redeploy, the model's retry count as it stands after the redeploy that
     *                           just finished; the model document may not have that value yet
     */
    private void triggerAutoDeployModels(List<String> addedNodes, Set<String> onlyModelIds, Integer retryTimesOverride) {
        ActionListener<SearchResponse> listener = ActionListener.wrap(res -> {
            if (res != null && res.getHits() != null && res.getHits().getTotalHits() != null) {
                Arrays
                    .stream(res.getHits().getHits())
                    .filter(x -> onlyModelIds == null || (x != null && onlyModelIds.contains(x.getId())))
                    .filter(x -> x != null && x.getSourceAsMap() != null && withinRetryBudget(x, retryTimesOverride))
                    .forEach(x -> {
                        ModelAutoRedeployArrangement modelAutoRedeployArrangement = ModelAutoRedeployArrangement
                            .builder()
                            .addedNodes(addedNodes)
                            .searchResponse(x)
                            .retryTimesOverride(retryTimesOverride)
                            .build();
                        boolean notExist = modelAutoRedeployArrangements.stream().noneMatch(y -> y.equals(modelAutoRedeployArrangement));
                        if (notExist)
                            modelAutoRedeployArrangements.add(modelAutoRedeployArrangement);
                    });
                lastQueueProgressMillis = currentTimeMillis.getAsLong();
                // The sync up cron must not run before this query has read the deployed models: on a full cluster restart it
                // would mark them DEPLOY_FAILED and they would never be picked up here. Once they are queued it is safe, and
                // it must not wait for the redeploy tasks to finish: a task that never completes, or finishes below the
                // success ratio, would otherwise leave a new cluster manager without the sync up cron (no routing table for
                // new nodes, no model state refresh, no task timeout).
                startCronjobAndClearListener();
                redeployAModel();
            } else {
                log.info("Could not find any models in the index, not performing auto reloading!");
                startCronjobAndClearListener();
            }
        }, e -> {
            if (e instanceof IndexNotFoundException) {
                log.info("Index not found, not performing auto reloading!");
            } else if (e instanceof ClusterBlockException) {
                log.info("Cluster status not ready, not performing auto reloading now!");
            } else {
                log.error("Failed to query need auto redeploy models, no action will be performed, addedNodes are: {}", addedNodes, e);
            }
            startCronjobAndClearListener();
        });

        queryRunningModels(listener);
    }

    private void triggerUndeployModelsOnDataNodes(List<String> dataNodeIds) {
        List<String> modelIds = new ArrayList<>();
        ActionListener<SearchResponse> listener = ActionListener.wrap(res -> {
            if (res != null && res.getHits() != null && res.getHits().getTotalHits() != null && res.getHits().getTotalHits().value() > 0) {
                Arrays.stream(res.getHits().getHits()).forEach(x -> modelIds.add(x.getId()));
                if (!modelIds.isEmpty()) {
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
        }, e -> { log.error("Failed to query need undeploy models, no action will be performed", e); });
        queryRunningModels(listener);
    }

    /**
     * A join for a model whose redeploy is running is recorded on that redeploy whatever the document's retry count says:
     * the running redeploy already wrote its own retry to the document, so the count can be at the limit even though
     * the redeploy then succeeds and resets it.
     */
    private boolean withinRetryBudget(SearchHit hit, Integer retryTimesOverride) {
        if (retryTimesOverride != null) {
            return retryTimesOverride < autoDeployMaxRetryTimes;
        }
        if (isRedeployInFlight(hit.getId())) {
            return true;
        }
        return currentRetryTimes(
            hit.getId(),
            (Integer) hit.getSourceAsMap().get(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD)
        ) < autoDeployMaxRetryTimes;
    }

    /**
     * The model's retry count: the document's, unless this node knows a lower one from the last few seconds (the document
     * may not show a reset yet). Known counts are kept only for the current cluster manager term, so a count another
     * cluster manager raised in the meantime is never undone; within a term, taking the lower value can at worst allow one
     * extra retry (if a reset write is lost), and never blocks a redeploy.
     */
    private int currentRetryTimes(String modelId, Integer documentRetryTimes) {
        int retryTimes = Optional.ofNullable(documentRetryTimes).orElse(0);
        long[] known = modelId == null ? null : knownRetryTimes.get(modelId);
        if (known != null) {
            if (isExpired(known) || known[2] != clusterManagerTerm()) {
                // stale, or from an earlier cluster manager term: another cluster manager may have used retries since
                knownRetryTimes.remove(modelId, known);
            } else {
                retryTimes = Math.min(retryTimes, (int) known[0]);
            }
        }
        return retryTimes;
    }

    private void rememberRetryTimes(String modelId, int retryTimes) {
        long term = clusterManagerTerm();
        if (modelId == null || term < 0 || !isElectedClusterManager()) {
            return;
        }
        knownRetryTimes.values().removeIf(this::isExpired);
        knownRetryTimes.put(modelId, new long[] { retryTimes, currentTimeMillis.getAsLong(), term });
    }

    private boolean isExpired(long[] known) {
        return currentTimeMillis.getAsLong() - known[1] > KNOWN_RETRY_TIMES_VALID_MS;
    }

    /** The term of the elected cluster manager, which changes with every election; -1 if unknown. */
    private long clusterManagerTerm() {
        try {
            return clusterService.state().term();
        } catch (RuntimeException e) {
            return -1;
        }
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
        if (modelAutoRedeployArrangement == null) {
            log.info("No more models in arrangement, skipping the redeployment");
            startCronjobAndClearListener();
            return;
        }
        String modelId = modelAutoRedeployArrangement.getSearchResponse().getId();
        List<String> addedNodes = modelAutoRedeployArrangement.getAddedNodes();
        InFlightRedeploy previous = inFlightRedeploys.get(modelId == null ? "" : modelId);
        InFlightRedeploy started = tryStartRedeploy(modelId, addedNodes);
        if (started == null) {
            log
                .info(
                    "Auto redeploy of model {} is already running, will redeploy again when it finishes. Added nodes: {}",
                    modelId,
                    addedNodes
                );
            redeployAModel();
            return;
        }
        if (previous != null && previous != started) {
            // the previous attempt went stale; make sure the nodes it was asked to cover are covered by this one
            Set<String> merged = new HashSet<>(Optional.ofNullable(addedNodes).orElse(List.of()));
            merged.addAll(previous.pendingAddedNodes);
            addedNodes = new ArrayList<>(merged);
        }
        List<String> planningWorkerNodes = (List<String>) modelAutoRedeployArrangement
            .getSearchResponse()
            .getSourceAsMap()
            .get(MLModel.PLANNING_WORKER_NODES_FIELD);
        // the queued search hit can be older than a redeploy of the model that finished since it was queued
        int retryTimesBefore = modelAutoRedeployArrangement.getRetryTimesOverride() != null
            ? modelAutoRedeployArrangement.getRetryTimesOverride()
            : currentRetryTimes(
                modelId,
                (Integer) modelAutoRedeployArrangement.getSearchResponse().getSourceAsMap().get(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD)
            );
        // nothing has been deployed or written yet; a follow-up from here starts from the current count
        started.retryTimes = retryTimesBefore;
        if (retryTimesBefore >= autoDeployMaxRetryTimes) {
            log.info("Not auto redeploying model {}: it has used its {} auto redeploy retries", modelId, autoDeployMaxRetryTimes);
            finishRedeploy(modelId, started);
            redeployAModel();
            return;
        }
        Boolean deployToAllNodes = (Boolean) Optional
            .ofNullable(modelAutoRedeployArrangement.getSearchResponse().getSourceAsMap().get(MLModel.DEPLOY_TO_ALL_NODES_FIELD))
            .orElse(false);
        // calculate node ids.
        String[] nodeIds = null;
        if (deployToAllNodes || !allowCustomDeploymentPlan) {
            nodeIds = new String[0];
        } else if (planningWorkerNodes != null && !planningWorkerNodes.isEmpty()) {
            // allow custom deploy plan and not deploy to all case, we need to check if the added nodes in planning worker nodes.
            List<String> needRedeployPlanningWorkerNodes = Arrays
                .stream(planningWorkerNodes.toArray(new String[0]))
                .filter(addedNodes::contains)
                .collect(Collectors.toList());
            nodeIds = !needRedeployPlanningWorkerNodes.isEmpty() ? planningWorkerNodes.toArray(new String[0]) : null;
        }

        if (nodeIds == null) {
            log
                .info(
                    "Allow custom deployment plan is true and deploy to all nodes is false and added nodes are not in planning worker nodes list, not to auto redeploy the model to the new nodes!"
                );
            finishRedeploy(modelId, started);
            redeployAModel();
            return;
        }

        ActionListener<MLDeployModelResponse> listener = ActionListener.wrap(res -> {
            log.info("Triggered model auto redeploy, task id is: {}, task status is: {}", res.getTaskId(), res.getStatus());
            started.taskId = res.getTaskId();
            if (started.taskId == null || (mlTaskManager != null && !mlTaskManager.contains(started.taskId))) {
                // the task already finished (or was never tracked) before its id was recorded
                finishRedeploy(modelId, started);
            }
        }, e -> {
            finishRedeploy(modelId, started);
            log
                .error(
                    "Exception occurred when auto redeploying the model, model id is: {}, exception is: {}, skipping current model auto redeploy and starting next model redeploy!",
                    modelId,
                    e.getMessage(),
                    e
                );
            redeployAModel();
        });

        // TODO: currently just provided tenantId null as auto re-deployer should work only in single tenant service. Will revisit this
        // later.
        started.retryTimes = retryTimesBefore + 1;
        rememberRetryTimes(modelId, started.retryTimes);
        mlModelManager.updateModel(modelId, null, false, ImmutableMap.of(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD, started.retryTimes));

        // TODO: currently just provided tenantId null as auto re-deployer should work only in single tenant service. Will revisit this
        // later.
        MLDeployModelRequest deployModelRequest = new MLDeployModelRequest(modelId, null, nodeIds, false, true, false);
        client.execute(MLDeployModelAction.INSTANCE, deployModelRequest, listener);
    }

    /**
     * Models this node is about to auto redeploy: those queued and not taken yet, and those whose redeploy is in flight
     * (taken from the queue, but the deploy task may not have set the model to DEPLOYING yet, and joins may be waiting
     * for a follow-up). Queued models are left out once the queue has made no progress for twice the task timeout: the
     * chain advances only when a deploy task completes, so a task that timed out can leave the rest of the queue waiting
     * for the next node join, and its models must not stay protected that long. An in-flight redeploy is bounded on its
     * own: it ends when its task leaves the task cache, or after {@link #IN_FLIGHT_REDEPLOY_STALE_AFTER_MS} if the deploy
     * request was never answered.
     */
    public Set<String> getQueuedModelIds() {
        Set<String> modelIds = new HashSet<>();
        inFlightRedeploys.forEach((modelId, redeploy) -> {
            if (!isStale(redeploy)) {
                modelIds.add(modelId);
            }
        });
        if (currentTimeMillis.getAsLong() - lastQueueProgressMillis <= queueStalledAfterMillis()) {
            modelAutoRedeployArrangements
                .stream()
                .map(ModelAutoRedeployArrangement::getSearchResponse)
                .filter(Objects::nonNull)
                .map(SearchHit::getId)
                .filter(Objects::nonNull)
                .forEach(modelIds::add);
        }
        return modelIds;
    }

    /**
     * How long the queue may make no progress before its models stop being protected: twice the task timeout, and at least
     * long enough for a timed-out head task to be noticed (tasks are timed out on a sync up round, so up to one timeout
     * plus one interval after they start). Both settings are dynamic, so read them on every call.
     */
    private long queueStalledAfterMillis() {
        long taskTimeoutMillis = clusterService.getClusterSettings().get(MLCommonsSettings.ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS) * 1000L;
        long syncUpIntervalMillis = clusterService.getClusterSettings().get(MLCommonsSettings.ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS)
            * 1000L;
        return Math.max(2 * taskTimeoutMillis, taskTimeoutMillis + 2 * syncUpIntervalMillis);
    }

    /**
     * Registers a new auto redeploy of the model, unless one is already running. In that case the added nodes are
     * remembered for a follow-up redeploy and null is returned.
     */
    synchronized InFlightRedeploy tryStartRedeploy(String modelId, List<String> addedNodes) {
        if (modelId == null) {
            return new InFlightRedeploy(currentTimeMillis.getAsLong());
        }
        InFlightRedeploy current = inFlightRedeploys.get(modelId);
        if (current != null && !isStale(current)) {
            if (addedNodes != null) {
                current.pendingAddedNodes.addAll(addedNodes);
            }
            current.rerunRequested = true;
            return null;
        }
        InFlightRedeploy started = new InFlightRedeploy(currentTimeMillis.getAsLong());
        if (current != null) {
            log
                .warn(
                    "Previous auto redeploy of model {} (task {}) is considered finished as it is no longer tracked",
                    modelId,
                    current.taskId
                );
        }
        inFlightRedeploys.put(modelId, started);
        return started;
    }

    private boolean isStale(InFlightRedeploy redeploy) {
        if (redeploy.taskId != null && mlTaskManager != null) {
            // A task still in the coordinator's cache is still running, however long it takes (a large model on many nodes
            // can take longer than any fixed limit); starting another redeploy next to it is what this class prevents. A
            // task whose remaining nodes all left is completed by TransportForwardAction as soon as they leave, which then
            // starts the follow-up for the nodes that joined meanwhile.
            return !mlTaskManager.contains(redeploy.taskId);
        }
        // the deploy response never arrived, so there is no task to follow
        return currentTimeMillis.getAsLong() - redeploy.startedAtMillis > IN_FLIGHT_REDEPLOY_STALE_AFTER_MS;
    }

    /**
     * Called on the deploy task's coordinating node when a deploy task finishes (all nodes replied, nodes left the cluster,
     * or the task timed out). If the task was an auto redeploy and more nodes joined while it was running, start one
     * follow-up redeploy of the model for them.
     */
    public void onDeployTaskCompleted(String modelId, String taskId) {
        if (modelId == null || taskId == null) {
            return;
        }
        InFlightRedeploy current = inFlightRedeploys.get(modelId);
        if (current == null || !taskId.equals(current.taskId)) {
            return;
        }
        // TransportForwardAction reports the result and moves on to the next queued model when a task completes. A task
        // that ends any other way (timed out, failed on the coordinating node) never got there, so the queue would stall.
        boolean completedWithResult = taskId.equals(current.resultTaskId);
        finishRedeploy(modelId, current);
        if (!completedWithResult && enableAutoReDeployModel && isElectedClusterManager()) {
            redeployAModel();
        }
    }

    /**
     * Called on the deploy task's coordinating node, before the task leaves the task cache, with whether the deployment
     * met the success ratio (which resets the model's retry count). The follow-up redeploy for nodes that joined in the
     * meantime starts from that count: the model document is updated only after the task left the cache.
     */
    public synchronized void onDeployTaskResult(String modelId, String taskId, boolean healthy) {
        if (modelId == null || taskId == null) {
            return;
        }
        InFlightRedeploy current = inFlightRedeploys.get(modelId);
        if (current != null && (current.taskId == null || taskId.equals(current.taskId))) {
            current.resultTaskId = taskId;
            current.resultHealthy = healthy;
        }
    }

    private void finishRedeploy(String modelId, InFlightRedeploy redeploy) {
        List<String> pendingNodes;
        int retryTimes;
        // under the same lock as tryStartRedeploy, so a join is either recorded before the entry is removed (and gets the
        // follow-up) or sees no entry (and starts its own redeploy); otherwise a join recorded on an entry that was just
        // removed would be lost
        synchronized (this) {
            if (modelId == null || !inFlightRedeploys.remove(modelId, redeploy)) {
                return;
            }
            boolean healthy = redeploy.resultHealthy && redeploy.resultTaskId != null && redeploy.resultTaskId.equals(redeploy.taskId);
            retryTimes = healthy ? 0 : redeploy.retryTimes;
            if (redeploy.taskId != null) {
                // a deploy ran: TransportForwardAction writes the reset (if healthy) after this, so remember the outcome
                rememberRetryTimes(modelId, retryTimes);
            }
            if (!redeploy.rerunRequested) {
                return;
            }
            pendingNodes = new ArrayList<>(redeploy.pendingAddedNodes);
        }
        if (!enableAutoReDeployModel) {
            return;
        }
        if (!isElectedClusterManager()) {
            // the new cluster manager redeploys deploy-to-all models to every node; a second redeploy from here would collide
            // with it. (A model with an explicit node list is redeployed by the new cluster manager only if it is one of the
            // model's nodes; the next join of a planned node covers it otherwise, as before.)
            log.info("Not redeploying model {} for nodes {}: this node is no longer the cluster manager", modelId, pendingNodes);
            return;
        }
        if (retryTimes >= autoDeployMaxRetryTimes) {
            log
                .info(
                    "Not redeploying model {} for nodes {}: the redeploy that just finished did not succeed and used the last auto redeploy retry",
                    modelId,
                    pendingNodes
                );
            return;
        }
        log.info("Nodes {} joined while model {} was being redeployed, redeploying it again", pendingNodes, modelId);
        triggerAutoDeployModels(pendingNodes, Set.of(modelId), retryTimes);
    }

    private boolean isElectedClusterManager() {
        try {
            return clusterService.state().nodes().isLocalNodeElectedClusterManager();
        } catch (RuntimeException e) {
            // no applied cluster state: keep the previous behavior
            return true;
        }
    }

    boolean isRedeployInFlight(String modelId) {
        if (modelId == null) {
            return false;
        }
        InFlightRedeploy current = inFlightRedeploys.get(modelId);
        return current != null && !isStale(current);
    }

    private synchronized void startCronjobAndClearListener() {
        boolean managerNode = clusterService.localNode().isClusterManagerNode();
        if (managerNode && startCronJobListener != null) {
            startCronJobListener.onResponse(true);
            startCronJobListener = null;
        }
    }

    static class InFlightRedeploy {
        final long startedAtMillis;
        final Set<String> pendingAddedNodes = ConcurrentHashMap.newKeySet();
        volatile boolean rerunRequested;
        volatile String taskId;
        // the retry count this redeploy wrote to the model document
        volatile int retryTimes;
        // result reported for a deploy task of this model before it left the task cache
        volatile String resultTaskId;
        volatile boolean resultHealthy;

        InFlightRedeploy(long startedAtMillis) {
            this.startedAtMillis = startedAtMillis;
        }
    }

    @Data
    @Builder
    static class ModelAutoRedeployArrangement {
        private List<String> addedNodes;
        private SearchHit searchResponse;
        private Integer retryTimesOverride;
    }

    public static class SearchRequestBuilderFactory {
        public SearchRequestBuilder getSearchRequestBuilder(OpenSearchClient client, SearchAction action) {
            return new SearchRequestBuilder(client, action);
        }
    }
}

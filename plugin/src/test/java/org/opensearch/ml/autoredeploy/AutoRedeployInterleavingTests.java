/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.autoredeploy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.opensearch.Version;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.OpenSearchClient;

/**
 * Exhaustive interleavings of auto redeploy on the cluster manager for one model: node joins, the deploy request being
 * accepted or failing, the deploy task finishing, the deploy task finishing before its response arrives, auto redeploy
 * being disabled, and the node losing cluster manager. Every reachable event sequence (depth-first, replayed from a fresh
 * world) is checked against these invariants:
 * <ul>
 *   <li>at most one deploy of the model is in flight at a time;</li>
 *   <li>every join is covered: some deploy request is issued at or after it, unless auto redeploy was disabled or the node
 *   stopped being cluster manager after it;</li>
 *   <li>every deploy request consumes exactly one lifetime retry, and nothing else does;</li>
 *   <li>once nothing is pending, no redeploy is recorded as in flight;</li>
 *   <li>a node that is no longer cluster manager starts no deploy.</li>
 * </ul>
 */
public class AutoRedeployInterleavingTests extends OpenSearchTestCase {

    private static final String MODEL_ID = "model1";
    private static final String CM = "cm";
    private static final int JOINS = 3;

    private enum Kind {
        JOIN,
        ACCEPT, // coordinator created the task and answered the deploy request
        ACCEPT_ALREADY_DONE, // the task finished before the response was handled
        FAIL_REQUEST,
        TASK_DONE,
        DISABLE,
        LOSE_CM
    }

    private final class World {
        final MLTaskManager taskManager = new MLTaskManager(
            mock(Client.class),
            mock(SdkClient.class),
            mock(ThreadPool.class),
            mock(MLIndicesHandler.class)
        );
        final Client client = mock(Client.class);
        final MLModelManager modelManager = mock(MLModelManager.class);
        final ClusterService clusterService = mock(ClusterService.class);
        final ClusterSettings clusterSettings;
        final Settings settings;
        final MLModelAutoReDeployer redeployer;
        final List<ActionListener<MLDeployModelResponse>> pendingResponses = new ArrayList<>();
        final List<String> acceptedTasks = new ArrayList<>();
        int deployRequests;
        int retryConsumed;
        int deploysWhileNotCm;
        int maxInFlight;
        int taskCounter;
        int joins;
        // index into the request counter at the time of each join; -1 if not required to be covered
        final List<Integer> joinRequestMark = new ArrayList<>();
        boolean disabled;
        boolean clusterManager = true;

        World() throws IOException {
            SearchRequestBuilder searchRequestBuilder = mock(SearchRequestBuilder.class);
            MLModelAutoReDeployer.SearchRequestBuilderFactory factory = mock(MLModelAutoReDeployer.SearchRequestBuilderFactory.class);
            when(factory.getSearchRequestBuilder(any(OpenSearchClient.class), any(SearchAction.class))).thenReturn(searchRequestBuilder);
            when(searchRequestBuilder.setIndices(anyString())).thenReturn(searchRequestBuilder);
            when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
            when(searchRequestBuilder.setSource(any(SearchSourceBuilder.class))).thenReturn(searchRequestBuilder);
            SearchResponse searchResponse = modelSearchResponse();
            doAnswer(inv -> {
                ((ActionListener<SearchResponse>) inv.getArgument(0)).onResponse(searchResponse);
                return null;
            }).when(searchRequestBuilder).execute(isA(ActionListener.class));
            doAnswer(inv -> {
                deployRequests++;
                if (!clusterManager) {
                    deploysWhileNotCm++;
                }
                pendingResponses.add(inv.getArgument(2));
                maxInFlight = Math.max(maxInFlight, inFlight());
                return null;
            }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), any(ActionListener.class));
            doAnswer(inv -> {
                Map<String, Object> fields = inv.getArgument(3);
                if (fields.containsKey(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD)) {
                    retryConsumed++;
                }
                return null;
            }).when(modelManager).updateModel(eq(MODEL_ID), isNull(), eq(false), any(Map.class));

            settings = Settings
                .builder()
                .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), false)
                .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 100)
                .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
                .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false)
                .build();
            DiscoveryNode localNode = new DiscoveryNode(
                CM,
                CM,
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
            );
            when(clusterService.localNode()).thenReturn(localNode);
            publishState(localNode, true);
            clusterSettings = clusterSetting(
                settings,
                ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE,
                ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES,
                ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN,
                ML_COMMONS_ONLY_RUN_ON_ML_NODE
            );
            when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
            redeployer = new MLModelAutoReDeployer(clusterService, client, settings, modelManager, taskManager, factory);
        }

        void publishState(DiscoveryNode localNode, boolean elected) {
            DiscoveryNodes.Builder nodes = DiscoveryNodes.builder().add(localNode).localNodeId(localNode.getId());
            if (elected) {
                nodes.clusterManagerNodeId(localNode.getId());
            }
            when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("test")).nodes(nodes).build());
        }

        /** Deploys requested and not yet finished (failed, or task done). */
        int inFlight() {
            return pendingResponses.size() + acceptedTasks.size();
        }

        List<Kind> enabled() {
            List<Kind> kinds = new ArrayList<>();
            if (joins < JOINS) {
                kinds.add(Kind.JOIN);
            }
            if (!pendingResponses.isEmpty()) {
                kinds.add(Kind.ACCEPT);
                kinds.add(Kind.ACCEPT_ALREADY_DONE);
                kinds.add(Kind.FAIL_REQUEST);
            }
            if (!acceptedTasks.isEmpty()) {
                kinds.add(Kind.TASK_DONE);
            }
            if (!disabled && joins > 0) {
                kinds.add(Kind.DISABLE);
            }
            if (clusterManager && joins > 0) {
                kinds.add(Kind.LOSE_CM);
            }
            return kinds;
        }

        MLTask newTask() {
            String taskId = "task" + (++taskCounter);
            MLTask task = MLTask
                .builder()
                .taskId(taskId)
                .modelId(MODEL_ID)
                .taskType(MLTaskType.DEPLOY_MODEL)
                .functionName(FunctionName.TEXT_EMBEDDING)
                .state(MLTaskState.CREATED)
                .async(true)
                .createTime(Instant.now())
                .lastUpdateTime(Instant.now())
                .build();
            taskManager.add(task, List.of("n1"));
            return task;
        }

        void apply(Kind kind) {
            switch (kind) {
                case JOIN -> {
                    joins++;
                    int mark = deployRequests;
                    // a join only reaches the redeployer on the elected cluster manager
                    if (clusterManager) {
                        redeployer.buildAutoReloadArrangement(List.of("join" + joins), CM);
                    }
                    joinRequestMark.add(disabled || !clusterManager ? -1 : mark);
                }
                case ACCEPT -> {
                    MLTask task = newTask();
                    acceptedTasks.add(task.getTaskId());
                    pendingResponses.remove(0).onResponse(new MLDeployModelResponse(task.getTaskId(), MLTaskType.DEPLOY_MODEL, "CREATED"));
                }
                case ACCEPT_ALREADY_DONE -> {
                    MLTask task = newTask();
                    taskManager.remove(task.getTaskId());
                    pendingResponses
                        .remove(0)
                        .onResponse(new MLDeployModelResponse(task.getTaskId(), MLTaskType.DEPLOY_MODEL, "COMPLETED"));
                }
                case FAIL_REQUEST -> pendingResponses.remove(0).onFailure(new RuntimeException("deploy request failed"));
                case TASK_DONE -> taskManager.remove(acceptedTasks.remove(0));
                case DISABLE -> {
                    disabled = true;
                    // joins before this point no longer have to be covered
                    joinRequestMark.replaceAll(m -> -1);
                    clusterSettings
                        .applySettings(Settings.builder().put(settings).put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), false).build());
                }
                case LOSE_CM -> {
                    clusterManager = false;
                    joinRequestMark.replaceAll(m -> -1);
                    publishState(clusterService.localNode(), false);
                }
            }
        }

        String check(boolean quiescent) {
            if (inFlight() > 1) {
                return "two deploys of the model in flight";
            }
            if (retryConsumed != deployRequests) {
                return "retries consumed " + retryConsumed + " for " + deployRequests + " deploy requests";
            }
            if (deploysWhileNotCm > 0) {
                return "deploy started after the node stopped being cluster manager";
            }
            if (quiescent) {
                for (int i = 0; i < joinRequestMark.size(); i++) {
                    int mark = joinRequestMark.get(i);
                    if (mark >= 0 && deployRequests <= mark) {
                        return "join " + (i + 1) + " never covered by a deploy";
                    }
                }
                if (redeployer.isRedeployInFlight(MODEL_ID)) {
                    return "redeploy still recorded in flight with nothing pending";
                }
            }
            return null;
        }
    }

    private static SearchResponse modelSearchResponse() throws IOException {
        SearchHit hit = new SearchHit(0, MODEL_ID, Map.of(), Map.of())
            .sourceRef(
                BytesReference
                    .bytes(
                        XContentFactory
                            .jsonBuilder()
                            .startObject()
                            .field(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD, 0)
                            .field(MLModel.DEPLOY_TO_ALL_NODES_FIELD, true)
                            .endObject()
                    )
            );
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits())
            .thenReturn(new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN));
        return searchResponse;
    }

    private int sequences;
    private final List<String> violations = new ArrayList<>();

    public void testAllInterleavings() throws Exception {
        explore(new ArrayList<>());
        logger.info("auto redeploy: {} complete sequences, {} states, {} violations", sequences, explored, violations.size());
        Map<String, Integer> byKind = new java.util.TreeMap<>();
        Map<String, String> example = new java.util.HashMap<>();
        for (String v : violations) {
            String k = v.substring(0, Math.max(v.indexOf(" after "), v.indexOf(" for ")));
            byKind.merge(k, 1, Integer::sum);
            example.merge(k, v, (a, b) -> a.length() <= b.length() ? a : b);
        }
        byKind.forEach((k, n) -> logger.info("violation kind: {} x{} shortest: {}", k, n, example.get(k)));
        assertTrue(
            violations.size() + " violations, first: " + violations.subList(0, Math.min(5, violations.size())),
            violations.isEmpty()
        );
    }

    private long explored;

    private void explore(List<Kind> prefix) throws Exception {
        if (++explored > 3_000_000) {
            throw new AssertionError("state space too large");
        }
        World world = new World();
        for (int i = 0; i < prefix.size(); i++) {
            world.apply(prefix.get(i));
            String v = world.check(false);
            if (v != null) {
                violations.add(v + " after " + prefix.subList(0, i + 1));
                return;
            }
        }
        List<Kind> next = world.enabled();
        // DISABLE and LOSE_CM are terminal-ish perturbations: allow them at most once each (enabled() already does)
        if (next.isEmpty() || prefix.size() > 14) {
            sequences++;
            String v = world.check(true);
            if (v != null) {
                violations.add(v + " for " + prefix);
            }
            return;
        }
        for (Kind k : next) {
            prefix.add(k);
            explore(prefix);
            prefix.remove(prefix.size() - 1);
        }
    }
}

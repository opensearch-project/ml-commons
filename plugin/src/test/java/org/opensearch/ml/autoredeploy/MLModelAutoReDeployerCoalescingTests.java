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
import static org.mockito.Mockito.verify;
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
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.XContentBuilder;
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
 * Node joins arrive in bursts. These tests check that a burst results in one redeploy task per model (plus at most one
 * follow-up for nodes that joined while it ran), that only real attempts consume the lifetime retry budget, and that the
 * sync up cron is started without waiting for redeploy tasks to finish.
 */
public class MLModelAutoReDeployerCoalescingTests extends OpenSearchTestCase {

    private static final String MODEL_ID = "model1";
    private static final String CLUSTER_MANAGER = "cm";

    @Mock
    private Client client;
    @Mock
    private MLModelManager mlModelManager;
    @Mock
    private SearchRequestBuilder searchRequestBuilder;
    @Mock
    private MLModelAutoReDeployer.SearchRequestBuilderFactory searchRequestBuilderFactory;

    private MLTaskManager mlTaskManager;
    private MLModelAutoReDeployer redeployer;
    private Settings settings;
    private ClusterService clusterService;
    private ClusterSettings clusterSettings;
    private final List<ActionListener<MLDeployModelResponse>> pendingDeployResponses = new ArrayList<>();
    private final List<MLDeployModelRequest> deployRequests = new ArrayList<>();
    private int taskCounter;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(searchRequestBuilderFactory.getSearchRequestBuilder(any(OpenSearchClient.class), any(SearchAction.class)))
            .thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setIndices(anyString())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSource(any(SearchSourceBuilder.class))).thenReturn(searchRequestBuilder);
        SearchResponse searchResponse = modelSearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));

        // deploy requests are answered by the test, so it controls when a redeploy is "in flight"
        doAnswer(invocation -> {
            deployRequests.add(invocation.getArgument(1));
            pendingDeployResponses.add(invocation.getArgument(2));
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), any(ActionListener.class));

        settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), false)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false)
            .build();
        clusterService = mock(ClusterService.class);
        DiscoveryNode localNode = new DiscoveryNode(
            CLUSTER_MANAGER,
            CLUSTER_MANAGER,
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.state())
            .thenReturn(
                org.opensearch.cluster.ClusterState
                    .builder(new org.opensearch.cluster.ClusterName("test"))
                    .nodes(
                        org.opensearch.cluster.node.DiscoveryNodes
                            .builder()
                            .add(localNode)
                            .localNodeId(localNode.getId())
                            .clusterManagerNodeId(localNode.getId())
                    )
                    .build()
            );
        clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE,
            ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES,
            ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN,
            ML_COMMONS_ONLY_RUN_ON_ML_NODE
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        mlTaskManager = new MLTaskManager(client, mock(SdkClient.class), mock(ThreadPool.class), mock(MLIndicesHandler.class));
        redeployer = new MLModelAutoReDeployer(
            clusterService,
            client,
            settings,
            mlModelManager,
            mlTaskManager,
            searchRequestBuilderFactory
        );
    }

    private static SearchResponse modelSearchResponse() throws IOException {
        XContentBuilder source = XContentFactory
            .jsonBuilder()
            .startObject()
            .field(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD, 0)
            .field(MLModel.DEPLOY_TO_ALL_NODES_FIELD, true)
            .endObject();
        SearchHit hit = new SearchHit(0, MODEL_ID, Map.of(), Map.of()).sourceRef(BytesReference.bytes(source));
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits())
            .thenReturn(new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN));
        return searchResponse;
    }

    /** Answers the oldest pending deploy request; the coordinator now tracks the task in its task cache. */
    private String acceptDeploy() {
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
        mlTaskManager.add(task, List.of("n1", "n2"));
        pendingDeployResponses.remove(0).onResponse(new MLDeployModelResponse(taskId, MLTaskType.DEPLOY_MODEL, "CREATED"));
        return taskId;
    }

    private void join(String... nodes) {
        redeployer.buildAutoReloadArrangement(List.of(nodes), CLUSTER_MANAGER);
    }

    private int retryCounterUpdates() {
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(mlModelManager, org.mockito.Mockito.atLeast(0)).updateModel(eq(MODEL_ID), isNull(), eq(false), fields.capture());
        return (int) fields.getAllValues().stream().filter(f -> f.containsKey(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD)).count();
    }

    public void testJoinBurst_startsOneRedeployTaskAndConsumesOneRetry() {
        join("n3");
        acceptDeploy();
        join("n4");
        join("n5");
        join("n6");
        join("n7");

        assertEquals(1, deployRequests.size());
        assertEquals(1, retryCounterUpdates());
        assertTrue(redeployer.isRedeployInFlight(MODEL_ID));
    }

    public void testJoinsDuringRedeploy_triggerExactlyOneFollowUpWhenItFinishes() {
        join("n3");
        String first = acceptDeploy();
        join("n4");
        join("n5");

        mlTaskManager.remove(first); // the coordinator finished the task

        assertEquals(2, deployRequests.size());
        assertEquals(2, retryCounterUpdates());
        assertTrue(redeployer.isRedeployInFlight(MODEL_ID));
        // deploy-to-all: the follow-up targets every eligible node
        assertEquals(0, deployRequests.get(1).getModelNodeIds().length);

        String second = acceptDeploy();
        mlTaskManager.remove(second);
        assertEquals("no joins happened during the follow-up, so nothing more to do", 2, deployRequests.size());
        assertFalse(redeployer.isRedeployInFlight(MODEL_ID));
    }

    public void testNoJoinDuringRedeploy_noFollowUp() {
        join("n3");
        String taskId = acceptDeploy();
        mlTaskManager.remove(taskId);

        assertEquals(1, deployRequests.size());
        assertFalse(redeployer.isRedeployInFlight(MODEL_ID));
        join("n4");
        assertEquals("a join after the redeploy finished starts a new one", 2, deployRequests.size());
    }

    public void testUnrelatedTaskRemoval_doesNotReleaseRedeploy() {
        join("n3");
        acceptDeploy();
        MLTask other = MLTask
            .builder()
            .taskId("userDeploy")
            .modelId(MODEL_ID)
            .taskType(MLTaskType.DEPLOY_MODEL)
            .state(MLTaskState.CREATED)
            .async(true)
            .createTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();
        mlTaskManager.add(other, List.of("n1"));
        mlTaskManager.remove("userDeploy");

        assertTrue(redeployer.isRedeployInFlight(MODEL_ID));
        join("n4");
        assertEquals(1, deployRequests.size());
    }

    public void testRedeployTaskNoLongerTracked_isTreatedAsFinished() {
        // the coordinator lost the task without the removal callback running (e.g. a lost response)
        MLTaskManager taskManager = mock(MLTaskManager.class);
        when(taskManager.contains(anyString())).thenReturn(true);
        redeployer = new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, taskManager, searchRequestBuilderFactory);
        join("n3");
        pendingDeployResponses.remove(0).onResponse(new MLDeployModelResponse("t1", MLTaskType.DEPLOY_MODEL, "CREATED"));
        assertTrue(redeployer.isRedeployInFlight(MODEL_ID));

        when(taskManager.contains("t1")).thenReturn(false);
        assertFalse(redeployer.isRedeployInFlight(MODEL_ID));
        join("n4");
        assertEquals(2, deployRequests.size());
    }

    public void testDeployRequestFails_releasesRedeploy() {
        join("n3");
        pendingDeployResponses.remove(0).onFailure(new RuntimeException("boom"));
        assertFalse(redeployer.isRedeployInFlight(MODEL_ID));
        join("n4");
        assertEquals(2, deployRequests.size());
    }

    public void testTaskFinishedBeforeResponse_releasesRedeploy() {
        join("n3");
        // response carries a task id that the coordinator no longer tracks
        pendingDeployResponses.remove(0).onResponse(new MLDeployModelResponse("gone", MLTaskType.DEPLOY_MODEL, "COMPLETED"));
        assertFalse(redeployer.isRedeployInFlight(MODEL_ID));
    }

    public void testAutoRedeployDisabledWhileRunning_noFollowUp() {
        join("n3");
        String taskId = acceptDeploy();
        join("n4");
        clusterSettings.applySettings(Settings.builder().put(settings).put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), false).build());
        mlTaskManager.remove(taskId);
        assertEquals(1, deployRequests.size());
        assertFalse(redeployer.isRedeployInFlight(MODEL_ID));
    }

    public void testJoinRecordedWhileRedeployCompletes_isNotLost() throws Exception {
        join("n3");
        String taskId = acceptDeploy();
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        // iterating the added nodes (while the join is recorded on the in-flight redeploy) parks the joining thread there
        List<String> blockingNodes = new ArrayList<>(List.of("n4")) {
            @Override
            public java.util.Iterator<String> iterator() {
                inside.countDown();
                try {
                    release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.iterator();
            }
        };
        Thread joiner = new Thread(() -> assertNull(redeployer.tryStartRedeploy(MODEL_ID, blockingNodes)));
        joiner.start();
        assertTrue(inside.await(10, java.util.concurrent.TimeUnit.SECONDS));
        // the running redeploy's task completes while the join is being recorded
        Thread completer = new Thread(() -> mlTaskManager.remove(taskId));
        completer.start();
        completer.join(300);
        release.countDown();
        joiner.join(10_000);
        completer.join(10_000);

        assertEquals("the join must get a follow-up redeploy", 2, deployRequests.size());
    }

    public void testRedeployFinishesAfterLosingClusterManager_noFollowUp() {
        join("n3");
        String taskId = acceptDeploy();
        join("n4");
        // this node is no longer the elected cluster manager; the new one redeploys for the joined nodes
        DiscoveryNode localNode = clusterService.localNode();
        when(clusterService.state())
            .thenReturn(
                org.opensearch.cluster.ClusterState
                    .builder(new org.opensearch.cluster.ClusterName("test"))
                    .nodes(org.opensearch.cluster.node.DiscoveryNodes.builder().add(localNode).localNodeId(localNode.getId()))
                    .build()
            );
        mlTaskManager.remove(taskId);

        assertEquals(1, deployRequests.size());
        assertFalse(redeployer.isRedeployInFlight(MODEL_ID));
    }
}

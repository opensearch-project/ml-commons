/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.autoredeploy;

import static org.mockito.Mockito.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.settings.MLCommonsSettings.*;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.client.OpenSearchClient;
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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class MLModelAutoReDeployerTests extends OpenSearchTestCase {
    @Mock
    private Client client;
    @Mock
    private MLModelManager mlModelManager;

    private MLModelAutoReDeployer mlModelAutoReDeployer;

    private final String clusterManagerNodeId = "mockClusterManagerNodeId";

    @Mock
    private SearchRequestBuilder searchRequestBuilder;

    @Mock
    private MLModelAutoReDeployer.SearchRequestBuilderFactory searchRequestBuilderFactory;

    private DiscoveryNode localNode = new DiscoveryNode(
        "mockClusterManagerNodeId",
        "mockClusterManagerNodeId",
        new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    private final List<String> addedNodes = List.of("addedMLNode");

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(searchRequestBuilderFactory.getSearchRequestBuilder(any(OpenSearchClient.class), any(SearchAction.class)))
            .thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setIndices(anyString())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSource(any(SearchSourceBuilder.class))).thenReturn(searchRequestBuilder);
    }

    public void test_buildAutoReloadArrangement_deployToAllNodes_isTrue_success() throws Exception {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));
        mockClusterDataNodes(clusterService);

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse searchResponse = buildDeployToAllNodesTrueSearchResponse("ModelResult.json");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        MLDeployModelResponse mlDeployModelResponse = mock(MLDeployModelResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(mlDeployModelResponse);
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), any(ActionListener.class));
        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_buildAutoReloadArrangement_searchResponseEmpty_failure() throws Exception {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));
        mockClusterDataNodes(clusterService);

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse emptyHitsResponse = mock(SearchResponse.class);
        SearchResponse emptyTotalHitsResponse = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[] { null }, null, Float.NaN);
        when(emptyTotalHitsResponse.getHits()).thenReturn(hits);
        when(emptyHitsResponse.getHits()).thenReturn(null);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(null);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(emptyHitsResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(emptyTotalHitsResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        MLDeployModelResponse mlDeployModelResponse = mock(MLDeployModelResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(mlDeployModelResponse);
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), any(ActionListener.class));
        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_buildAutoReloadArrangement_allowCustomDeployIsFalse_success() throws Exception {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));
        mockClusterDataNodes(clusterService);

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse searchResponse = buildDeployToAllNodesTrueSearchResponse("ModelDeployToAllFalseResult.json");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        MLDeployModelResponse mlDeployModelResponse = mock(MLDeployModelResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(mlDeployModelResponse);
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), any(ActionListener.class));
        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_buildAutoReloadArrangement_searchIndex_exception() throws Exception {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));
        mockClusterDataNodes(clusterService);

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("runtime exception!"));
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_buildAutoReloadArrangement_deployModel_exception() throws Exception {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));
        mockClusterDataNodes(clusterService);

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse searchResponse = buildDeployToAllNodesTrueSearchResponse("ModelResult.json");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("runtime exception!"));
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), any(ActionListener.class));
        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_buildAutoReloadArrangement_with_deployToAllNodes_isTrue() throws Exception {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));
        mockClusterDataNodes(clusterService);

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse searchResponse = buildDeployToAllNodesTrueSearchResponse("ModelResult.json");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_buildAutoReloadArrangement_with_deployToAllNodes_isFalse_allowCustomTrue_enter_checkPlanningWorkerNodes()
        throws Exception {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));
        mockClusterDataNodes(clusterService);

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse searchResponse = buildDeployToAllNodesTrueSearchResponse("ModelDeployToAllFalseResult.json");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_buildAutoReloadArrangement_with_deployToAllNodesFalse_allowCustomTrue_planningWorkerNodesEmpty_enterNodeIdsNull()
        throws Exception {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));
        mockClusterDataNodes(clusterService);

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse searchResponse = buildDeployToAllNodesTrueSearchResponse("ModelDeployToAllFalsePlanningWokerNodesEmptyResult.json");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_undeployModelsOnDataNodes_success() throws Exception {
        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        DiscoveryNode dataNode = mock(DiscoveryNode.class);
        when(dataNode.getId()).thenReturn("mockDataNodeId");
        final Map<String, DiscoveryNode> dataNodes = Collections.unmodifiableMap(Map.of("0", dataNode));
        when(discoveryNodes.getDataNodes()).thenReturn(dataNodes);
        when(clusterState.nodes()).thenReturn(discoveryNodes);

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.state()).thenReturn(clusterState);

        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse searchResponse = buildDeployToAllNodesTrueSearchResponse("ModelResult.json");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        MLUndeployModelNodesResponse mlUndeployModelNodesResponse = mock(MLUndeployModelNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlUndeployModelNodesResponse);
            return null;
        }).when(client).execute(any(MLUndeployModelAction.class), any(MLUndeployModelNodesRequest.class), isA(ActionListener.class));
        Consumer<Boolean> consumer = mlModelAutoReDeployer.undeployModelsOnDataNodesConsumer();
        consumer.accept(true);
    }

    public void test_undeployModelsOnDataNodes_undeployModel_failed() throws Exception {
        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        DiscoveryNode dataNode = mock(DiscoveryNode.class);
        when(dataNode.getId()).thenReturn("mockDataNodeId");
        final Map<String, DiscoveryNode> dataNodes = Collections.unmodifiableMap(Map.of("0", dataNode));
        when(discoveryNodes.getDataNodes()).thenReturn(dataNodes);
        when(clusterState.nodes()).thenReturn(discoveryNodes);

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.state()).thenReturn(clusterState);

        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse searchResponse = buildDeployToAllNodesTrueSearchResponse("ModelResult.json");
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("runtime exception!"));
            return null;
        }).when(client).execute(any(MLUndeployModelAction.class), any(MLUndeployModelNodesRequest.class), isA(ActionListener.class));
        Consumer<Boolean> consumer = mlModelAutoReDeployer.undeployModelsOnDataNodesConsumer();
        consumer.accept(true);
    }

    public void test_undeployModelsOnDataNodes_undeployModel_responseNull() throws Exception {
        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        DiscoveryNode dataNode = mock(DiscoveryNode.class);
        when(dataNode.getId()).thenReturn("mockDataNodeId");
        final Map<String, DiscoveryNode> dataNodes = Collections.unmodifiableMap(Map.of("0", dataNode));
        when(discoveryNodes.getDataNodes()).thenReturn(dataNodes);
        when(clusterState.nodes()).thenReturn(discoveryNodes);

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.state()).thenReturn(clusterState);

        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        SearchResponse emptyHitsResponse = mock(SearchResponse.class);
        SearchResponse emptyTotalHitsResponse = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[] { null }, null, Float.NaN);
        when(emptyTotalHitsResponse.getHits()).thenReturn(hits);
        when(emptyHitsResponse.getHits()).thenReturn(null);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(null);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(emptyHitsResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(emptyTotalHitsResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));
        MLUndeployModelNodesResponse mlUndeployModelNodesResponse = mock(MLUndeployModelNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlUndeployModelNodesResponse);
            return null;
        }).when(client).execute(any(MLUndeployModelAction.class), any(MLUndeployModelNodesRequest.class), isA(ActionListener.class));
        Consumer<Boolean> consumer = mlModelAutoReDeployer.undeployModelsOnDataNodesConsumer();
        consumer.accept(true);
        consumer.accept(true);
        consumer.accept(true);
    }

    public void test_undeployModelsOnDataNodes_runOnMlNodesIsFalse_notRun() {
        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        DiscoveryNode dataNode = mock(DiscoveryNode.class);
        when(dataNode.getId()).thenReturn("mockDataNodeId");
        final Map<String, DiscoveryNode> dataNodes = Collections.unmodifiableMap(Map.of("0", dataNode));
        when(discoveryNodes.getDataNodes()).thenReturn(dataNodes);
        when(clusterState.nodes()).thenReturn(discoveryNodes);

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.state()).thenReturn(clusterState);

        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), false)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );
        Consumer<Boolean> consumer = mlModelAutoReDeployer.undeployModelsOnDataNodesConsumer();
        consumer.accept(true);
    }

    public void test_buildAutoReloadArrangement_with_autoRedeploy_isFalse() {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), false)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_buildAutoReloadArrangement_localNode_is_not_manager() {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        ClusterService clusterService = mock(ClusterService.class);

        DiscoveryNode notManagerNode = new DiscoveryNode(
            "mockClusterManagerNodeId",
            "notManagerNodeId",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        when(clusterService.localNode()).thenReturn(notManagerNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodes, clusterManagerNodeId);
    }

    public void test_redeployAModel_with_autoRedeploy_isFalse() {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), false)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        mlModelAutoReDeployer.redeployAModel();
    }

    public void test_redeployAModel_with_needRedeployArray_isEmpty() {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
            .put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true)
            .build();

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.getClusterSettings()).thenReturn(getClusterSettings(settings));

        mlModelAutoReDeployer = spy(
            new MLModelAutoReDeployer(clusterService, client, settings, mlModelManager, searchRequestBuilderFactory)
        );

        mlModelAutoReDeployer.redeployAModel();
    }

    private SearchResponse buildDeployToAllNodesTrueSearchResponse(String file) throws Exception {
        MLModel mlModel = buildModelWithJsonFile(file);
        return createResponseWithModel(mlModel);
    }

    private SearchResponse createResponseWithModel(MLModel mlModel) throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        SearchHit hit = new SearchHit(0).sourceRef(BytesReference.bytes(content));
        SearchHits hits = new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }

    private MLModel buildModelWithJsonFile(String file) throws Exception {
        Path modelContentPath = Path.of(getClass().getResource(file).toURI());
        String modelContent = Files.readString(modelContentPath);
        return MLModel.parse(TestHelper.parser(modelContent), null);
    }

    private void mockClusterDataNodes(ClusterService clusterService) {
        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        final Map<String, DiscoveryNode> dataNodes = Collections.unmodifiableMap(Map.of("dataNodeId", mock(DiscoveryNode.class)));
        when(discoveryNodes.getDataNodes()).thenReturn(dataNodes);
        when(discoveryNodes.getSize()).thenReturn(2); // a ml node join cluster.
        when(clusterState.nodes()).thenReturn(discoveryNodes);
        when(clusterService.state()).thenReturn(clusterState);
    }

    private ClusterSettings getClusterSettings(Settings settings) {
        return clusterSetting(
            settings,
            ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE,
            ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES,
            ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN,
            ML_COMMONS_ONLY_RUN_ON_ML_NODE
        );
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.utils.RestActionUtils.OPENSEARCH_DASHBOARDS_USER_AGENT;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ASYNC;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.UI_METADATA_EXCLUDE;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.Version;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.ThreadPool;

public class RestActionUtilsTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    Map<String, String> param;
    FakeRestRequest fakeRestRequest;
    String algoName = FunctionName.KMEANS.name();
    String urlPath = MachineLearningPlugin.ML_BASE_URI + "/_train/" + algoName;

    @Before
    public void setup() {
        param = Map.of(PARAMETER_ALGORITHM, algoName);
        fakeRestRequest = createRestRequest(param);

    }

    private FakeRestRequest createRestRequest(Map<String, String> param) {
        return createRestRequest(param, urlPath, RestRequest.Method.POST);
    }

    private FakeRestRequest createRestRequest(Map<String, String> param, String urlPath, RestRequest.Method method) {
        return new FakeRestRequest.Builder(xContentRegistry()).withMethod(method).withPath(urlPath).withParams(param).build();
    }

    public void testGetAlgorithm() {
        String paramValue = RestActionUtils.getAlgorithm(fakeRestRequest);
        assertEquals(algoName, paramValue);
    }

    public void testGetAlgorithm_EmptyValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain algorithm!");
        fakeRestRequest = createRestRequest(Map.of(PARAMETER_ALGORITHM, ""));
        RestActionUtils.getAlgorithm(fakeRestRequest);
    }

    @Test
    public void testReturnContent() {
        RestRequest request = mock(RestRequest.class);
        when(request.paramAsBoolean("return_content", false)).thenReturn(true);
        Assert.assertTrue(RestActionUtils.returnContent(request));

        when(request.paramAsBoolean("return_content", false)).thenReturn(false);
        Assert.assertFalse(RestActionUtils.returnContent(request));
    }

    @Test
    public void testGetAllNodes() {

        DiscoveryNode localNode = new DiscoveryNode(
            "mockClusterManagerNodeId",
            "mockClusterManagerNodeId",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        DiscoveryNode dataNode = mock(DiscoveryNode.class);
        ClusterService clusterService = mock(ClusterService.class);

        when(dataNode.getId()).thenReturn("mockDataNodeId");
        final Map<String, DiscoveryNode> dataNodes = Map.of("0", dataNode);
        when(discoveryNodes.getDataNodes()).thenReturn(dataNodes);
        when(clusterState.nodes()).thenReturn(discoveryNodes);
        when(clusterService.localNode()).thenReturn(localNode);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.nodes()).thenReturn(discoveryNodes);
        when(discoveryNodes.getSize()).thenReturn(2); // Assuming 2 nodes in the cluster
        // Mock two discovery nodes
        DiscoveryNode node1 = mock(DiscoveryNode.class);
        DiscoveryNode node2 = mock(DiscoveryNode.class);
        when(node1.getId()).thenReturn("node1");
        when(node2.getId()).thenReturn("node2");
        when(discoveryNodes.iterator()).thenReturn(Arrays.asList(node1, node2).iterator());

        String[] nodeIds = RestActionUtils.getAllNodes(clusterService);
        Assert.assertArrayEquals(new String[] { "node1", "node2" }, nodeIds);
    }

    public void testIsAsync() {
        fakeRestRequest = createRestRequest(Map.of(PARAMETER_ASYNC, "true"));
        boolean isAsync = RestActionUtils.isAsync(fakeRestRequest);
        assertTrue(isAsync);
    }

    public void testGetParameterId() {
        String modelId = "testModelId";
        param = Map.of(PARAMETER_MODEL_ID, modelId);
        fakeRestRequest = createRestRequest(param, "_plugins/_ml/models/" + modelId, RestRequest.Method.GET);
        String paramValue = RestActionUtils.getParameterId(fakeRestRequest, PARAMETER_MODEL_ID);
        assertEquals(modelId, paramValue);
    }

    public void testGetParameterId_EmptyValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain " + PARAMETER_MODEL_ID);
        param = Map.of(PARAMETER_MODEL_ID, "");
        fakeRestRequest = createRestRequest(param, "_plugins/_ml/models/testModelId", RestRequest.Method.GET);
        RestActionUtils.getParameterId(fakeRestRequest, PARAMETER_MODEL_ID);
    }

    public void testGetSourceContext_FromDashboards() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Arrays.asList(OPENSEARCH_DASHBOARDS_USER_AGENT));
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .withHeaders(headers)
            .build();
        SearchSourceBuilder testSearchSourceBuilder = new SearchSourceBuilder();
        testSearchSourceBuilder.fetchSource(new String[] { "a" }, new String[] { "b" });
        FetchSourceContext sourceContext = RestActionUtils.getSourceContext(request, testSearchSourceBuilder);
        assertNotNull(sourceContext);
    }

    public void testGetSourceContext_FromClient_EmptyExcludes() {
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .build();
        SearchSourceBuilder testSearchSourceBuilder = new SearchSourceBuilder();
        testSearchSourceBuilder.fetchSource(new String[] { "a" }, new String[0]);
        FetchSourceContext sourceContext = RestActionUtils.getSourceContext(request, testSearchSourceBuilder);
        assertArrayEquals(UI_METADATA_EXCLUDE, sourceContext.excludes());
    }

    public void testGetSourceContext_FromClient_WithExcludes() {
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .build();
        SearchSourceBuilder testSearchSourceBuilder = new SearchSourceBuilder();
        testSearchSourceBuilder.fetchSource(new String[] { "a" }, new String[] { "b" });
        FetchSourceContext sourceContext = RestActionUtils.getSourceContext(request, testSearchSourceBuilder);
        assertEquals(sourceContext.excludes().length, 2);
    }

    public void test_getSourceContext_fetchSource_null_dashboardUserAgent_null() {
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .build();
        SearchSourceBuilder testSearchSourceBuilder = new SearchSourceBuilder();
        FetchSourceContext sourceContext = RestActionUtils.getSourceContext(request, testSearchSourceBuilder);
        assertNotNull(sourceContext);
    }

    public void test_getSourceContext_fetchSource_null_dashboardUserAgent_notNull() {
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .withHeaders(Map.of("User-Agent", Arrays.asList("OpenSearch Dashboards")))
            .build();
        SearchSourceBuilder testSearchSourceBuilder = new SearchSourceBuilder();
        FetchSourceContext sourceContext = RestActionUtils.getSourceContext(request, testSearchSourceBuilder);
        assertNotNull(sourceContext);
    }

    public void test_getFetchSourceContext_return_modelContent() {
        FetchSourceContext result = RestActionUtils.getFetchSourceContext(true);
        assertNotNull(result);
    }

    public void test_getFetchSourceContext_not_return_modelContent() {
        FetchSourceContext result = RestActionUtils.getFetchSourceContext(false);
        assertNotNull(result);
    }

    public void test_getAllNodes() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState clusterState = mock(ClusterState.class);
        DiscoveryNodes discoveryNodes = mock(DiscoveryNodes.class);
        final Map<String, DiscoveryNode> dataNodes = Map.of("dataNodeId", mock(DiscoveryNode.class));
        when(discoveryNodes.getDataNodes()).thenReturn(dataNodes);
        when(discoveryNodes.getSize()).thenReturn(1);
        when(discoveryNodes.iterator()).thenReturn(dataNodes.values().iterator());
        when(clusterState.nodes()).thenReturn(discoveryNodes);
        when(clusterService.state()).thenReturn(clusterState);
        String[] result = RestActionUtils.getAllNodes(clusterService);
        assertNotNull(result);
        assertEquals(1, result.length);
    }

    public void test_onFailure() {
        fakeRestRequest = createRestRequest(Map.of(PARAMETER_ALGORITHM, ""));
        RestActionUtils.onFailure(mock(RestChannel.class), RestStatus.CREATED, "error", new IllegalArgumentException("test"));
    }

    public void test_splitCommaSeparatedParam() {
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .withHeaders(Map.of("User-Agent", Arrays.asList("OpenSearch Dashboards")))
            .build();
        Optional<String[]> result = RestActionUtils.splitCommaSeparatedParam(request, PARAMETER_ALGORITHM);
        assertNotNull(result);
        assertNotNull(result.get());
    }

    public void test_getStringParam() {
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .withHeaders(Map.of("User-Agent", Arrays.asList("OpenSearch Dashboards")))
            .build();
        Optional<String> result = RestActionUtils.getStringParam(request, PARAMETER_ALGORITHM);
        assertNotNull(result);
        assertNotNull(result.get());
    }

    public void test_getUserContext() {
        Client client = mock(Client.class);
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        ThreadContext threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "myuser||myrole");
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        User user = RestActionUtils.getUserContext(client);
        assertNotNull(user);
    }

    @Test
    public void testIsSuperAdminUser() {
        ClusterService clusterService = mock(ClusterService.class);
        Client client = mock(Client.class);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);

        when(clusterService.getSettings())
            .thenReturn(Settings.builder().putList(RestActionUtils.SECURITY_AUTHCZ_ADMIN_DN, "cn=admin").build());
        when(client.threadPool()).thenReturn(mock(ThreadPool.class));
        when(client.threadPool().getThreadContext()).thenReturn(threadContext);

        threadContext.putTransient(RestActionUtils.OPENDISTRO_SECURITY_SSL_PRINCIPAL, "cn=admin");

        boolean isAdmin = RestActionUtils.isSuperAdminUser(clusterService, client);
        Assert.assertTrue(isAdmin);
    }

    @Test
    public void testIsSuperAdminUser_NotAdmin() {
        ClusterService clusterService = mock(ClusterService.class);
        Client client = mock(Client.class);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);

        when(clusterService.getSettings())
            .thenReturn(Settings.builder().putList(RestActionUtils.SECURITY_AUTHCZ_ADMIN_DN, "cn=admin").build());
        when(client.threadPool()).thenReturn(mock(ThreadPool.class));
        when(client.threadPool().getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(RestActionUtils.OPENDISTRO_SECURITY_SSL_PRINCIPAL, "cn=notadmin");

        boolean isAdmin = RestActionUtils.isSuperAdminUser(clusterService, client);
        Assert.assertFalse(isAdmin);
    }
}

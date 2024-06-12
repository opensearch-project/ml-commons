/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.task.MLPredictTaskRunnerTests.USER_STRING;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableList;

public class ConnectorAccessControlHelperTests extends OpenSearchTestCase {

    @Mock
    ClusterService clusterService;

    @Mock
    Client client;

    @Mock
    private ActionListener<Boolean> actionListener;

    @Mock
    private ActionListener<Connector> getConnectorActionListener;

    @Mock
    private ThreadPool threadPool;

    ThreadContext threadContext;

    private ConnectorAccessControlHelper connectorAccessControlHelper;

    private GetResponse getResponse;

    private User user;

    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private static TestThreadPool testThreadPool = new TestThreadPool(
        ConnectorAccessControlHelperTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        sdkClient = new LocalClusterIndicesClient(client, xContentRegistry);
        threadContext = new ThreadContext(settings);
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        connectorAccessControlHelper = spy(new ConnectorAccessControlHelper(clusterService, settings));
        user = User.parse("mockUser|role-1,role-2|null");

        getResponse = createGetResponse(null);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(any())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void test_hasPermission_user_null_return_true() {
        HttpConnector httpConnector = mock(HttpConnector.class);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(null, httpConnector);
        assertTrue(hasPermission);
    }

    @Test
    public void test_hasPermission_connectorAccessControl_not_enabled_return_true() {
        HttpConnector httpConnector = mock(HttpConnector.class);
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        ConnectorAccessControlHelper connectorAccessControlHelper = new ConnectorAccessControlHelper(clusterService, settings);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, httpConnector);
        assertTrue(hasPermission);
    }

    @Test
    public void test_hasPermission_connectorOwner_is_null_return_true() {
        HttpConnector httpConnector = mock(HttpConnector.class);
        when(httpConnector.getOwner()).thenReturn(null);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, httpConnector);
        assertTrue(hasPermission);
    }

    @Test
    public void test_hasPermission_user_is_admin_return_true() {
        User user = User.parse("admin|role-1|all_access");
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, mock(HttpConnector.class));
        assertTrue(hasPermission);
    }

    @Test
    public void test_hasPermission_connector_isPublic_return_true() {
        HttpConnector httpConnector = mock(HttpConnector.class);
        when(httpConnector.getAccess()).thenReturn(AccessMode.PUBLIC);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, httpConnector);
        assertTrue(hasPermission);
    }

    @Test
    public void test_hasPermission_connector_isPrivate_userIsOwner_return_true() {
        HttpConnector httpConnector = mock(HttpConnector.class);
        when(httpConnector.getAccess()).thenReturn(AccessMode.PRIVATE);
        when(httpConnector.getOwner()).thenReturn(user);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, httpConnector);
        assertTrue(hasPermission);
    }

    @Test
    public void test_hasPermission_connector_isPrivate_userIsNotOwner_return_false() {
        HttpConnector httpConnector = mock(HttpConnector.class);
        when(httpConnector.getAccess()).thenReturn(AccessMode.PRIVATE);
        User user1 = User.parse(USER_STRING);
        when(httpConnector.getOwner()).thenReturn(user);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user1, httpConnector);
        assertFalse(hasPermission);
    }

    @Test
    public void test_hasPermission_connector_isRestricted_userHasBackendRole_return_true() {
        HttpConnector httpConnector = mock(HttpConnector.class);
        when(httpConnector.getAccess()).thenReturn(AccessMode.RESTRICTED);
        when(httpConnector.getBackendRoles()).thenReturn(ImmutableList.of("role-1"));
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, httpConnector);
        assertTrue(hasPermission);
    }

    @Test
    public void test_hasPermission_connector_isRestricted_userNotHasBackendRole_return_false() {
        HttpConnector httpConnector = mock(HttpConnector.class);
        when(httpConnector.getAccess()).thenReturn(AccessMode.RESTRICTED);
        when(httpConnector.getBackendRoles()).thenReturn(ImmutableList.of("role-3"));
        when(httpConnector.getOwner()).thenReturn(user);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, httpConnector);
        assertFalse(hasPermission);
    }

    // todo: will remove this later
    public void test_validateConnectorAccess_user_isAdmin_return_true_old() {
        String userString = "admin|role-1|all_access";
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        ThreadContext threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, userString);

        connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        verify(actionListener).onResponse(true);
    }

    @Test
    public void test_validateConnectorAccess_user_isAdmin_return_true() {
        String userString = "admin|role-1|all_access";
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        ThreadContext threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, userString);

        connectorAccessControlHelper.validateConnectorAccess(sdkClient, client, "anyId", null, mlFeatureEnabledSetting, actionListener);
        verify(actionListener).onResponse(true);
    }

    // todo will remove later.
    public void test_validateConnectorAccess_user_isNotAdmin_hasNoBackendRole_return_false_old() {
        GetResponse getResponse = createGetResponse(ImmutableList.of("role-3"));
        Client client = mock(Client.class);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);

        connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        verify(actionListener).onResponse(false);
    }

    @Test
    public void test_validateConnectorAccess_user_isNotAdmin_hasNoBackendRole_return_false() throws Exception {
        // Mock the client thread pool
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Set up user context
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        // Create HttpConnector
        HttpConnector httpConnector = HttpConnector.builder()
                .name("testConnector")
                .protocol(ConnectorProtocols.HTTP)
                .owner(user)
                .description("This is test connector")
                .backendRoles(Collections.singletonList("role-3"))
                .accessMode(AccessMode.RESTRICTED)
                .build();

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener.onResponse(httpConnector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());

        // Execute the validation
        connectorAccessControlHelper.validateConnectorAccess(sdkClient, client, "anyId", null, mlFeatureEnabledSetting, actionListener);

        // Verify the action listener was called with false
        verify(actionListener).onResponse(false);
    }

    @Test
    public void test_validateConnectorAccess_user_isNotAdmin_hasBackendRole_return_true() {
        connectorAccessControlHelper.validateConnectorAccess(sdkClient, client, "anyId", null, mlFeatureEnabledSetting, actionListener);
        verify(actionListener).onResponse(true);
    }

    // todo will remove later
    public void test_validateConnectorAccess_user_isNotAdmin_hasBackendRole_return_true_old() {
        connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        verify(actionListener).onResponse(true);
    }

    @Test
    public void test_validateConnectorAccess_connectorNotFound_return_false() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(5);
            listener.onFailure(new OpenSearchStatusException("Failed to find connector", RestStatus.NOT_FOUND));
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);

        // connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        connectorAccessControlHelper.validateConnectorAccess(sdkClient, client, "anyId", null, mlFeatureEnabledSetting, actionListener);
        verify(actionListener, times(1)).onFailure(any(OpenSearchStatusException.class));
    }

    // todo will remove later
    public void test_validateConnectorAccess_connectorNotFound_return_false_old() {
        Client client = mock(Client.class);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);

        connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        verify(actionListener, times(1)).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void test_validateConnectorAccess_searchConnectorException_return_false() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(5);
            listener.onFailure(new RuntimeException("Failed to find connector"));
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);

        // connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        connectorAccessControlHelper.validateConnectorAccess(sdkClient, client, "anyId", null, mlFeatureEnabledSetting, actionListener);
        verify(actionListener, times(1)).onFailure(any(RuntimeException.class));
    }

    // todo will remove later
    public void test_validateConnectorAccess_searchConnectorException_return_false_old() {
        Client client = mock(Client.class);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).get(any(), any());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);

        connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        verify(actionListener).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    public void test_skipConnectorAccessControl_userIsNull_return_true() {
        boolean skip = connectorAccessControlHelper.skipConnectorAccessControl(null);
        assertTrue(skip);
    }

    @Test
    public void test_skipConnectorAccessControl_connectorAccessControl_notEnabled_return_true() {
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        ConnectorAccessControlHelper connectorAccessControlHelper = new ConnectorAccessControlHelper(clusterService, settings);
        boolean skip = connectorAccessControlHelper.skipConnectorAccessControl(user);
        assertTrue(skip);
    }

    @Test
    public void test_skipConnectorAccessControl_userIsAdmin_return_true() {
        User user = User.parse("admin|role-1|all_access");
        boolean skip = connectorAccessControlHelper.skipConnectorAccessControl(user);
        assertTrue(skip);
    }

    @Test
    public void test_accessControlNotEnabled_connectorAccessControl_notEnabled_return_true() {
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        ConnectorAccessControlHelper connectorAccessControlHelper = new ConnectorAccessControlHelper(clusterService, settings);
        boolean skip = connectorAccessControlHelper.accessControlNotEnabled(user);
        assertTrue(skip);
    }

    @Test
    public void test_accessControlNotEnabled_userIsNull_return_true() {
        boolean notEnabled = connectorAccessControlHelper.accessControlNotEnabled(null);
        assertTrue(notEnabled);
    }

    @Test
    public void test_addUserBackendRolesFilter_nullQuery() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchSourceBuilder result = connectorAccessControlHelper.addUserBackendRolesFilter(user, searchSourceBuilder);
        assertNotNull(result);
    }

    @Test
    public void test_addUserBackendRolesFilter_boolQuery() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new BoolQueryBuilder());
        SearchSourceBuilder result = connectorAccessControlHelper.addUserBackendRolesFilter(user, searchSourceBuilder);
        assertEquals("bool", result.query().getName());
    }

    @Test
    public void test_addUserBackendRolesFilter_nonBoolQuery() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        SearchSourceBuilder result = connectorAccessControlHelper.addUserBackendRolesFilter(user, searchSourceBuilder);
        assertEquals("bool", result.query().getName());
    }

    @Test
    public void testGetConnectorHappyCase() throws IOException, InterruptedException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder()
            .index(CommonValue.ML_CONNECTOR_INDEX)
            .id("connectorId")
            .build();
        GetResponse getResponse = prepareConnector();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<Connector> latchedActionListener = new LatchedActionListener<>(getConnectorActionListener, latch);
        connectorAccessControlHelper
            .getConnector(
                sdkClient,
                client,
                client.threadPool().getThreadContext().newStoredContext(true),
                getRequest,
                "connectorId",
                latchedActionListener
            );
        latch.await();

        ArgumentCaptor<GetRequest> requestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        verify(client, times(1)).get(requestCaptor.capture());
        assertEquals(CommonValue.ML_CONNECTOR_INDEX, requestCaptor.getValue().index());
    }

    private GetResponse createGetResponse(List<String> backendRoles) {
        HttpConnector httpConnector = HttpConnector
            .builder()
            .name("testConnector")
            .protocol(ConnectorProtocols.HTTP)
            .owner(user)
            .description("This is test connector")
            .backendRoles(Optional.ofNullable(backendRoles).orElse(ImmutableList.of("role-1")))
            .accessMode(AccessMode.RESTRICTED)
            .build();
        XContentBuilder content;
        try {
            content = httpConnector.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult(CommonValue.ML_MODEL_GROUP_INDEX, "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

    public GetResponse prepareConnector() throws IOException {
        HttpConnector httpConnector = HttpConnector.builder().name("test_connector").protocol("http").build();
        XContentBuilder content = httpConnector.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}

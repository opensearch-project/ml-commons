package org.opensearch.ml.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.task.MLPredictTaskRunnerTests.USER_STRING;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.template.DetachedConnector;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
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
    private ThreadPool threadPool;

    ThreadContext threadContext;

    private ConnectorAccessControlHelper connectorAccessControlHelper;

    private GetResponse getResponse;

    private User user;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        threadContext = new ThreadContext(settings);
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        connectorAccessControlHelper = new ConnectorAccessControlHelper(clusterService, settings);
        user = User.parse("mockUser|role-1,role-2|null");

        getResponse = createGetResponse(null);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void test_hasPermission_user_null_return_true() {
        DetachedConnector detachedConnector = mock(DetachedConnector.class);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(null, detachedConnector);
        assertTrue(hasPermission);
    }

    public void test_hasPermission_connectorAccessControl_not_enabled_return_true() {
        DetachedConnector detachedConnector = mock(DetachedConnector.class);
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        ConnectorAccessControlHelper connectorAccessControlHelper = new ConnectorAccessControlHelper(clusterService, settings);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, detachedConnector);
        assertTrue(hasPermission);
    }

    public void test_hasPermission_connectorOwner_is_null_return_true() {
        DetachedConnector detachedConnector = mock(DetachedConnector.class);
        when(detachedConnector.getOwner()).thenReturn(null);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, detachedConnector);
        assertTrue(hasPermission);
    }

    public void test_hasPermission_user_is_admin_return_true() {
        User user = User.parse("admin|role-1|all_access");
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, mock(DetachedConnector.class));
        assertTrue(hasPermission);
    }

    public void test_hasPermission_connector_isPublic_return_true() {
        DetachedConnector detachedConnector = mock(DetachedConnector.class);
        when(detachedConnector.getAccess()).thenReturn(AccessMode.PUBLIC);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, detachedConnector);
        assertTrue(hasPermission);
    }

    public void test_hasPermission_connector_isPrivate_userIsOwner_return_true() {
        DetachedConnector detachedConnector = mock(DetachedConnector.class);
        when(detachedConnector.getAccess()).thenReturn(AccessMode.PRIVATE);
        when(detachedConnector.getOwner()).thenReturn(user);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, detachedConnector);
        assertTrue(hasPermission);
    }

    public void test_hasPermission_connector_isPrivate_userIsNotOwner_return_false() {
        DetachedConnector detachedConnector = mock(DetachedConnector.class);
        when(detachedConnector.getAccess()).thenReturn(AccessMode.PRIVATE);
        User user1 = User.parse(USER_STRING);
        when(detachedConnector.getOwner()).thenReturn(user);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user1, detachedConnector);
        assertFalse(hasPermission);
    }

    public void test_hasPermission_connector_isRestricted_userHasBackendRole_return_true() {
        DetachedConnector detachedConnector = mock(DetachedConnector.class);
        when(detachedConnector.getAccess()).thenReturn(AccessMode.RESTRICTED);
        when(detachedConnector.getBackendRoles()).thenReturn(ImmutableList.of("role-1"));
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, detachedConnector);
        assertTrue(hasPermission);
    }

    public void test_hasPermission_connector_isRestricted_userNotHasBackendRole_return_false() {
        DetachedConnector detachedConnector = mock(DetachedConnector.class);
        when(detachedConnector.getAccess()).thenReturn(AccessMode.RESTRICTED);
        when(detachedConnector.getBackendRoles()).thenReturn(ImmutableList.of("role-3"));
        when(detachedConnector.getOwner()).thenReturn(user);
        boolean hasPermission = connectorAccessControlHelper.hasPermission(user, detachedConnector);
        assertFalse(hasPermission);
    }

    public void test_validateConnectorAccess_user_isAdmin_return_true() {
        String userString = "admin|role-1|all_access";
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        ThreadContext threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, userString);

        connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        verify(actionListener).onResponse(true);
    }

    public void test_validateConnectorAccess_user_isNotAdmin_hasNoBackendRole_return_false() {
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

    public void test_validateConnectorAccess_user_isNotAdmin_hasBackendRole_return_true() {
        connectorAccessControlHelper.validateConnectorAccess(client, "anyId", actionListener);
        verify(actionListener).onResponse(true);
    }

    public void test_validateConnectorAccess_connectorNotFound_return_false() {
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
        verify(actionListener, times(1)).onFailure(any(MLResourceNotFoundException.class));
    }

    public void test_validateConnectorAccess_searchConnectorException_return_false() {
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
        verify(actionListener).onFailure(any(IllegalStateException.class));
    }

    public void test_skipConnectorAccessControl_userIsNull_return_true() {
        boolean skip = connectorAccessControlHelper.skipConnectorAccessControl(null);
        assertTrue(skip);
    }

    public void test_skipConnectorAccessControl_connectorAccessControl_notEnabled_return_true() {
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        ConnectorAccessControlHelper connectorAccessControlHelper = new ConnectorAccessControlHelper(clusterService, settings);
        boolean skip = connectorAccessControlHelper.skipConnectorAccessControl(user);
        assertTrue(skip);
    }

    public void test_skipConnectorAccessControl_userIsAdmin_return_true() {
        User user = User.parse("admin|role-1|all_access");
        boolean skip = connectorAccessControlHelper.skipConnectorAccessControl(user);
        assertTrue(skip);
    }

    public void test_accessControlNotEnabled_connectorAccessControl_notEnabled_return_true() {
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        ConnectorAccessControlHelper connectorAccessControlHelper = new ConnectorAccessControlHelper(clusterService, settings);
        boolean skip = connectorAccessControlHelper.accessControlNotEnabled(user);
        assertTrue(skip);
    }

    public void test_accessControlNotEnabled_userIsNull_return_true() {
        boolean notEnabled = connectorAccessControlHelper.accessControlNotEnabled(null);
        assertTrue(notEnabled);
    }

    public void test_addUserBackendRolesFilter_nullQuery() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchSourceBuilder result = connectorAccessControlHelper.addUserBackendRolesFilter(user, searchSourceBuilder);
        assertNotNull(result);
    }

    public void test_addUserBackendRolesFilter_boolQuery() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new BoolQueryBuilder());
        SearchSourceBuilder result = connectorAccessControlHelper.addUserBackendRolesFilter(user, searchSourceBuilder);
        assertEquals("bool", result.query().getName());
    }

    public void test_addUserBackendRolesFilter_nonBoolQuery() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        SearchSourceBuilder result = connectorAccessControlHelper.addUserBackendRolesFilter(user, searchSourceBuilder);
        assertEquals("bool", result.query().getName());
    }

    private GetResponse createGetResponse(List<String> backendRoles) {
        DetachedConnector detachedConnector = DetachedConnector
            .builder()
            .name("testConnector")
            .description("This is test connector")
            .owner(user)
            .backendRoles(Optional.ofNullable(backendRoles).orElse(ImmutableList.of("role-1")))
            .access(AccessMode.RESTRICTED)
            .build();
        XContentBuilder content = null;
        try {
            content = detachedConnector.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult(CommonValue.ML_MODEL_GROUP_INDEX, "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}

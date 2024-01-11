/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.task.MLPredictTaskRunnerTests.USER_STRING;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportCreateConnectorActionTests extends OpenSearchTestCase {

    private TransportCreateConnectorAction action;

    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private Client client;
    @Mock
    private MLEngine mlEngine;
    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private TransportService transportService;
    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private Task task;

    @Mock
    private MLCreateConnectorRequest request;

    private MLCreateConnectorInput input;

    @Mock
    ActionListener<MLCreateConnectorResponse> actionListener;

    @Mock
    private ThreadPool threadPool;

    ThreadContext threadContext;

    @Mock
    private ClusterService clusterService;

    private Settings settings;

    private static final List<String> TRUSTED_CONNECTOR_ENDPOINTS_REGEXES = List
        .of("^https://runtime\\.sagemaker\\..*\\.amazonaws\\.com/.*$", "^https://api\\.openai\\.com/.*$", "^https://api\\.cohere\\.ai/.*$");

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings
            .builder()
            .putList(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), TRUSTED_CONNECTOR_ENDPOINTS_REGEXES)
            .build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX,
            ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        action = new TransportCreateConnectorAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            client,
            mlEngine,
            connectorAccessControlHelper,
            settings,
            clusterService,
            mlModelManager
        );
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        List<ConnectorAction> actions = new ArrayList<>();
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.PREDICT)
                    .method("POST")
                    .url("https://${parameters.endpoint}/v1/completions")
                    .build()
            );

        Map<String, String> parameters = Map.of("endpoint", "api.openai.com");
        Map<String, String> credential = Map.of("access_key", "mockKey", "secret_key", "mockSecret");
        input = MLCreateConnectorInput
            .builder()
            .name("test_name")
            .version("1")
            .actions(actions)
            .parameters(parameters)
            .protocol(ConnectorProtocols.HTTP)
            .credential(credential)
            .build();
        when(request.getMlCreateConnectorInput()).thenReturn(input);
    }

    public void test_execute_connectorAccessControl_notEnabled_success() {
        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(true);
        input.setAddAllBackendRoles(null);
        input.setBackendRoles(null);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        action.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLCreateConnectorResponse.class));
    }

    public void test_execute_connectorAccessControl_notEnabled_withPermissionInfo_exception() {
        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(true);
        input.setBackendRoles(null);
        input.setAddAllBackendRoles(true);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You cannot specify connector access control parameters because the Security plugin or connector access control is disabled on your cluster.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_execute_connectorAccessControlEnabled_success() {
        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(false);
        input.setAddAllBackendRoles(false);
        input.setBackendRoles(List.of("role1", "role2"));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        action.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLCreateConnectorResponse.class));
    }

    public void test_execute_connectorAccessControlEnabled_missingPermissionInfo_defaultToPrivate() {
        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(false);
        input.setAddAllBackendRoles(null);
        input.setBackendRoles(null);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        action.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLCreateConnectorResponse.class));
    }

    public void test_execute_connectorAccessControlEnabled_adminSpecifyAllBackendRoles_exception() {
        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(false);
        when(connectorAccessControlHelper.isAdmin(any(User.class))).thenReturn(true);
        input.setAddAllBackendRoles(true);
        input.setBackendRoles(null);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Admin can't add all backend roles", argumentCaptor.getValue().getMessage()
        );
    }

    public void test_execute_connectorAccessControlEnabled_specifyBackendRolesForPublicConnector_exception() {
        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(false);
        input.setAddAllBackendRoles(true);
        input.setAccess(AccessMode.PUBLIC);
        input.setBackendRoles(null);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You can specify backend roles only for a connector with the restricted access mode.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_execute_connectorAccessControlEnabled_userNoBackendRoles_exception() {
        Client client = mock(Client.class);
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        ThreadContext threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "myuser||myrole");
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(false);
        input.setAddAllBackendRoles(true);
        input.setBackendRoles(null);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        TransportCreateConnectorAction action = new TransportCreateConnectorAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            client,
            mlEngine,
            connectorAccessControlHelper,
            settings,
            clusterService,
            mlModelManager
        );
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You must have at least one backend role to create a connector.", argumentCaptor.getValue().getMessage());
    }

    public void test_execute_connectorAccessControlEnabled_parameterConflict_exception() {
        Client client = mock(Client.class);
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        ThreadContext threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "myuser|role1|myrole");
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(false);
        input.setAddAllBackendRoles(true);
        input.setBackendRoles(List.of("role1", "role2"));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        TransportCreateConnectorAction action = new TransportCreateConnectorAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            client,
            mlEngine,
            connectorAccessControlHelper,
            settings,
            clusterService,
            mlModelManager
        );
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You can't specify backend roles and add all backend roles to true at same time.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_execute_connectorAccessControlEnabled_specifyNotBelongedRole_exception() {
        Client client = mock(Client.class);
        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        ThreadContext threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "myuser|role1|myrole");
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(false);
        input.setAddAllBackendRoles(false);
        input.setBackendRoles(List.of("role1", "role2"));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
        TransportCreateConnectorAction action = new TransportCreateConnectorAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            client,
            mlEngine,
            connectorAccessControlHelper,
            settings,
            clusterService,
            mlModelManager
        );
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have the backend roles specified.", argumentCaptor.getValue().getMessage());
    }

    public void test_execute_dryRun_connector_creation() {
        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(false);
        input.setAddAllBackendRoles(false);
        input.setBackendRoles(List.of("role1", "role2"));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));

        MLCreateConnectorInput mlCreateConnectorInput = mock(MLCreateConnectorInput.class);
        when(mlCreateConnectorInput.getName()).thenReturn(MLCreateConnectorInput.DRY_RUN_CONNECTOR_NAME);
        when(mlCreateConnectorInput.isDryRun()).thenReturn(true);
        MLCreateConnectorRequest request = new MLCreateConnectorRequest(mlCreateConnectorInput);
        action.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLCreateConnectorResponse.class));
    }

    public void test_execute_URL_notMatchingExpression_exception() {
        List<ConnectorAction> actions = new ArrayList<>();
        actions
            .add(
                ConnectorAction
                    .builder()
                    .actionType(ConnectorAction.ActionType.PREDICT)
                    .method("POST")
                    .url("https://${parameters.endpoint}/v1/completions")
                    .build()
            );

        MLCreateConnectorInput mlCreateConnectorInput = MLCreateConnectorInput
            .builder()
            .name(randomAlphaOfLength(5))
            .description(randomAlphaOfLength(10))
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .actions(actions)
            .build();
        MLCreateConnectorRequest request = new MLCreateConnectorRequest(mlCreateConnectorInput);

        Map<String, String> parameters = Map.of("endpoint", "api.openai1.com");
        mlCreateConnectorInput.setParameters(parameters);
        TransportCreateConnectorAction action = new TransportCreateConnectorAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            client,
            mlEngine,
            connectorAccessControlHelper,
            settings,
            clusterService,
            mlModelManager
        );
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Connector URL is not matching the trusted connector endpoint regex, URL is: https://api.openai1.com/v1/completions",
            argumentCaptor.getValue().getMessage()
        );
    }
}

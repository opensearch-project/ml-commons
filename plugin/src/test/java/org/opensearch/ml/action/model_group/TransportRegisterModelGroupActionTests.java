/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.ml.common.ModelAccessMode;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportRegisterModelGroupActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private TransportService transportService;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Task task;

    @Mock
    private Client client;
    @Mock
    private ActionFilters actionFilters;

    @Mock
    private ActionListener<MLRegisterModelGroupResponse> actionListener;

    @Mock
    private ActionListener<IndexResponse> indexActionistener;

    @Mock
    private IndexResponse indexResponse;

    ThreadContext threadContext;

    private TransportRegisterModelGroupAction transportRegisterModelGroupAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    private final List<String> backendRoles = Arrays.asList("IT", "HR");

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        transportRegisterModelGroupAction = new TransportRegisterModelGroupAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            threadPool,
            client,
            clusterService,
            modelAccessControlHelper
        );
        assertNotNull(transportRegisterModelGroupAction);

        when(indexResponse.getId()).thenReturn("modelGroupID");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initModelGroupIndexIfAbsent(any());

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void test_SuccessAddAllBackendRolesTrue() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_SuccessPublic() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, ModelAccessMode.PUBLIC, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_ExceptionAllAccessFieldsNull() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User must specify at least one backend role or make the model public/private",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_ModelAccessModeNullAddAllBackendRolesTrue() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_Success() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, ModelAccessMode.PUBLIC, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_BackendRolesProvidedWithPublic() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, ModelAccessMode.PUBLIC, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User cannot specify backend roles to a public/private model group", argumentCaptor.getValue().getMessage());
    }

    public void test_BackendRolesProvidedWithPrivate() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, ModelAccessMode.PRIVATE, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User cannot specify backend roles to a public/private model group", argumentCaptor.getValue().getMessage());
    }

    public void test_RestrictedAndAdminSpecifiedAddAllBackendRoles() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "admin|admin|all_access");
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, ModelAccessMode.RESTRICTED, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Admin user cannot specify add all backend roles to a model group", argumentCaptor.getValue().getMessage());
    }

    public void test_RestrictedAndUserWithNoBackendRolesSetAddAllBackendRolesTrue() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex||engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, ModelAccessMode.RESTRICTED, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Current user doesn't have any backend role", argumentCaptor.getValue().getMessage());
    }

    public void test_RestrictedAndUserSpecifiedNoBackendRolesField() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, ModelAccessMode.RESTRICTED, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User have to specify backend roles or set add all backend roles to true for a restricted model group",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_RestrictedAndUserSpecifiedBothBackendRolesField() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(backendRoles, ModelAccessMode.RESTRICTED, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "User cannot specify add all backed roles to true and backend roles not empty",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_RestrictedAndUserSpecifiedIncorrectBackendRoles() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        List<String> incorrectBackendRole = Arrays.asList("Finance");

        MLRegisterModelGroupRequest actionRequest = prepareRequest(incorrectBackendRole, ModelAccessMode.RESTRICTED, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User cannot specify backend roles that doesn't belong to the current user", argumentCaptor.getValue().getMessage());
    }

    public void test_SuccessSecurityDisabledCluster() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_ExceptionFailedToInitModelGroupIndex() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    public void test_ExceptionSecurityDisabledCluster() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, true);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Cluster security plugin not enabled or model access control no enabled, can't pass access control data in request body",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_ExceptionFailedToIndexModelGroup() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new Exception("Index Not Found"));
            return null;
        }).when(client).index(any(), any());

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Index Not Found", argumentCaptor.getValue().getMessage());
    }

    public void test_ExceptionInitModelGroupIndexIfAbsent() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onFailure(new Exception("Index Not Found"));
            return null;
        }).when(mlIndicesHandler).initModelGroupIndexIfAbsent(any());

        MLRegisterModelGroupRequest actionRequest = prepareRequest(null, null, null);
        transportRegisterModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Index Not Found", argumentCaptor.getValue().getMessage());
    }

    private MLRegisterModelGroupRequest prepareRequest(
        List<String> backendRoles,
        ModelAccessMode modelAccessMode,
        Boolean isAddAllBackendRoles
    ) {
        MLRegisterModelGroupInput registerModelGroupInput = MLRegisterModelGroupInput
            .builder()
            .name("modelGroupName")
            .description("This is a test model group")
            .backendRoles(backendRoles)
            .modelAccessMode(modelAccessMode)
            .isAddAllBackendRoles(isAddAllBackendRoles)
            .build();
        return new MLRegisterModelGroupRequest(registerModelGroupInput);
    }

}

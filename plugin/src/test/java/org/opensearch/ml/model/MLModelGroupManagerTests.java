/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.ml.action.model_group.TransportRegisterModelGroupAction;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Ignore
public class MLModelGroupManagerTests extends OpenSearchTestCase {
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
    private ActionListener<String> actionListener;

    @Mock
    private IndexResponse indexResponse;

    ThreadContext threadContext;

    private TransportRegisterModelGroupAction transportRegisterModelGroupAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;
    @Mock
    private MLModelGroupManager mlModelGroupManager;

    private final List<String> backendRoles = Arrays.asList("IT", "HR");

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        mlModelGroupManager = new MLModelGroupManager(mlIndicesHandler, client, clusterService, modelAccessControlHelper);
        assertNotNull(mlModelGroupManager);

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

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_SuccessPublic() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.PUBLIC, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_ExceptionAllAccessFieldsNull() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "You must specify at least one backend role or make the model group public/private for registering it.",
                argumentCaptor.getValue().getMessage()
        );
    }

    public void test_ModelAccessModeNullAddAllBackendRolesTrue() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_BackendRolesProvidedWithPublic() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.PUBLIC, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You can specify backend roles only for a model group with the restricted access mode.", argumentCaptor.getValue().getMessage());
    }

    public void test_BackendRolesProvidedWithPrivate() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.PRIVATE, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You can specify backend roles only for a model group with the restricted access mode.", argumentCaptor.getValue().getMessage());
    }

    public void test_AdminSpecifiedAddAllBackendRolesForRestricted() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "admin|admin|all_access");
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.RESTRICTED, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Admin users cannot add all backend roles to a model group.", argumentCaptor.getValue().getMessage());
    }

    public void test_UserWithNoBackendRolesSpecifiedRestricted() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex||engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.RESTRICTED, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You must have at least one backend role to register a restricted model group.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_UserSpecifiedRestrictedButNoBackendRolesFieldF() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.RESTRICTED, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You must specify one or more backend roles or add all backend roles to register a restricted model group.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_RestrictedAndUserSpecifiedBothBackendRolesField() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(backendRoles, AccessMode.RESTRICTED, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You cannot specify backend roles and add all backend roles at the same time.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_RestrictedAndUserSpecifiedIncorrectBackendRoles() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        List<String> incorrectBackendRole = Arrays.asList("Finance");

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(incorrectBackendRole, AccessMode.RESTRICTED, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have the backend roles specified.", argumentCaptor.getValue().getMessage());
    }

    public void test_SuccessSecurityDisabledCluster() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_ExceptionSecurityDisabledCluster() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster.",
                argumentCaptor.getValue().getMessage()
        );
    }

    public void test_ExceptionFailedToInitModelGroupIndex() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    public void test_ExceptionFailedToIndexModelGroup() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new Exception("Index Not Found"));
            return null;
        }).when(client).index(any(), any());

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
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

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Index Not Found", argumentCaptor.getValue().getMessage());
    }

    private MLRegisterModelGroupInput prepareRequest(List<String> backendRoles, AccessMode modelAccessMode, Boolean isAddAllBackendRoles) {
        return MLRegisterModelGroupInput
            .builder()
            .name("modelGroupName")
            .description("This is a test model group")
            .backendRoles(backendRoles)
            .modelAccessMode(modelAccessMode)
            .isAddAllBackendRoles(isAddAllBackendRoles)
            .build();
    }

}

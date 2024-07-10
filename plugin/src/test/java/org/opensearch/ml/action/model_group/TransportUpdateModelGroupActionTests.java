/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.TotalHits;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.sdkclient.SdkClientFactory;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportUpdateModelGroupActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private String indexName = "testIndex";

    @Mock
    private TransportService transportService;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Task task;

    @Mock
    private Client client;

    SdkClient sdkClient;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ActionListener<MLUpdateModelGroupResponse> actionListener;

    @Mock
    private UpdateResponse updateResponse;

    ThreadContext threadContext;

    private TransportUpdateModelGroupAction transportUpdateModelGroupAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;
    @Mock
    private MLModelGroupManager mlModelGroupManager;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private String ownerString = "bob|IT,HR|myTenant";
    private List<String> backendRoles = Arrays.asList("IT");

    private static TestThreadPool testThreadPool = new TestThreadPool(
        TransportUpdateModelGroupActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, xContentRegistry, settings);
        threadContext = new ThreadContext(settings);
        transportUpdateModelGroupAction = new TransportUpdateModelGroupAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            xContentRegistry,
            clusterService,
            modelAccessControlHelper,
            mlModelGroupManager,
            mlFeatureEnabledSetting
        );
        assertNotNull(transportUpdateModelGroupAction);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        MLModelGroup mlModelGroup = MLModelGroup
            .builder()
            .modelGroupId("testModelGroupId")
            .name("testModelGroup")
            .description("This is test model Group")
            .owner(User.parse(ownerString))
            .backendRoles(backendRoles)
            .access("restricted")
            .build();
        XContentBuilder content = mlModelGroup.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult(indexName, "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);

        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        SearchResponse searchResponse = createModelGroupSearchResponse(0);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any());

        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void test_NonOwnerChangingAccessContentException() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Only owner or admin can update access control data.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_OwnerNoMoreHasPermissionException() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You don't have the specified backend role to update this model group. For more information, contact your administrator.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void test_NoAccessUserUpdatingModelGroupException() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);
        when(modelAccessControlHelper.isUserHasBackendRole(any(), any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to update this model group.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_BackendRolesProvidedWithPrivate() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isUserHasBackendRole(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PRIVATE, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You can specify backend roles only for a model group with the restricted access mode.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_BackendRolesProvidedWithPublic() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isUserHasBackendRole(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PUBLIC, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You can specify backend roles only for a model group with the restricted access mode.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_AdminSpecifiedAddAllBackendRolesForRestricted() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Admin users cannot add all backend roles to a model group.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_UserWithNoBackendRolesSpecifiedRestricted() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "bob||engineering,operations");
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have any backend roles.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_UserSpecifiedRestrictedButNoBackendRolesField() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, false);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You have to specify backend roles when add all backend roles is set to false.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_RestrictedAndUserSpecifiedBothBackendRolesFields() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(backendRoles, AccessMode.RESTRICTED, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You cannot specify backend roles and add all backend roles at the same time.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void test_RestrictedAndUserSpecifiedIncorrectBackendRoles() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        List<String> incorrectBackendRole = Arrays.asList("Finance");

        MLUpdateModelGroupRequest actionRequest = prepareRequest(incorrectBackendRole, AccessMode.RESTRICTED, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have the backend roles specified.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_SuccessPrivateWithOwnerAsUser() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PRIVATE, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_SuccessRestricedWithOwnerAsUser() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "bob|IT,HR|myTenant");
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_SuccessPublicWithAdminAsUser() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PUBLIC, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_SuccessRestrictedWithAdminAsUser() throws InterruptedException {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(backendRoles, AccessMode.RESTRICTED, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_SuccessNonOwnerUpdatingWithNoAccessContent() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);
        when(modelAccessControlHelper.isUserHasBackendRole(any(), any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_FailedToFindModelGroupException() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new MLResourceNotFoundException("Failed to find model group"));
        when(client.get(any(GetRequest.class))).thenReturn(future);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model group", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_FailedToGetModelGroupException() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new Exception("Failed to get model group"));
        when(client.get(any(GetRequest.class))).thenReturn(future);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get model group", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_ModelGroupIndexNotFoundException() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new IndexNotFoundException("Failed to find model group"));
        when(client.get(any(GetRequest.class))).thenReturn(future);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model group", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_FailedToUpdatetModelGroupException() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new MLException("Failed to update Model Group"));
            return null;
        }).when(client).update(any(), any());

        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PUBLIC, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update Model Group", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_SuccessSecurityDisabledCluster() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_ModelGroupNameNotUnique() throws IOException, InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        SearchResponse searchResponse = createModelGroupSearchResponse(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any());

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "The name you provided is already being used by another model with ID: model_group_ID. Please provide a different name",
                argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void test_ExceptionSecurityDisabledCluster() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLUpdateModelGroupResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster.",
                argumentCaptor.getValue().getMessage()
        );
    }

    private MLUpdateModelGroupRequest prepareRequest(List<String> backendRoles, AccessMode modelAccessMode, Boolean isAddAllBackendRoles) {
        MLUpdateModelGroupInput UpdateModelGroupInput = MLUpdateModelGroupInput
            .builder()
            .modelGroupID("testModelGroupId")
            .name("modelGroupName")
            .description("This is a test model group")
            .backendRoles(backendRoles)
            .modelAccessMode(modelAccessMode)
            .isAddAllBackendRoles(isAddAllBackendRoles)
            .build();
        return new MLUpdateModelGroupRequest(UpdateModelGroupInput);
    }

    private SearchResponse createModelGroupSearchResponse(long totalHits) throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"access\": \"public\",\n"
            + "                    \"latest_version\": 0,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"_id\": \"model_group_ID\",\n"
            + "                    \"name\": \"model_group_IT\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit modelGroup = SearchHit.fromXContent(TestHelper.parser(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { modelGroup }, new TotalHits(totalHits, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }

}

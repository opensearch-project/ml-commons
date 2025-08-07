/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.MLModelGroup.MLModelGroupBuilder;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class ModelAccessControlHelperTests extends OpenSearchTestCase {

    @Mock
    ClusterService clusterService;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private ActionListener<Boolean> actionListener;

    @Mock
    private ThreadPool threadPool;

    ThreadContext threadContext;

    private ModelAccessControlHelper modelAccessControlHelper;

    GetResponse getResponse;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().put(ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        threadContext = new ThreadContext(settings);
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        modelAccessControlHelper = new ModelAccessControlHelper(clusterService, settings);
        assertNotNull(modelAccessControlHelper);

        // TODO Remove when all calls are migrated to SdkClient version
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void setupModelGroup(String owner, String access, List<String> backendRoles) throws IOException {
        getResponse = modelGroupBuilder(backendRoles, access, owner);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
    }

    // TODO Remove when all calls are migrated to SdkClient version
    public void test_UndefinedModelGroupID_NoSdkClient() {
        modelAccessControlHelper.validateModelGroupAccess(null, null, null, client, actionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    public void test_UndefinedModelGroupID() {
        modelAccessControlHelper
            .validateModelGroupAccess(null, mlFeatureEnabledSetting, null, null, null, client, sdkClient, actionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    // TODO Remove when all calls are migrated to SdkClient version
    public void test_UndefinedOwner_NoSdkClient() throws IOException {
        getResponse = modelGroupBuilder(null, null, null);
        modelAccessControlHelper.validateModelGroupAccess(null, "testGroupID", null, client, actionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    public void test_UndefinedOwner() throws IOException {
        getResponse = modelGroupBuilder(null, null, null);
        modelAccessControlHelper
            .validateModelGroupAccess(null, mlFeatureEnabledSetting, null, null, "testGroupID", client, sdkClient, actionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    // TODO Remove when all calls are migrated to SdkClient version
    public void test_ExceptionEmptyBackendRoles_NoSdkClient() throws IOException {
        String owner = "owner|IT,HR|myTenant";
        User user = User.parse("owner|IT,HR|myTenant");
        getResponse = modelGroupBuilder(null, AccessMode.RESTRICTED.getValue(), owner);
        modelAccessControlHelper.validateModelGroupAccess(user, "testGroupID", null, client, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Backend roles shouldn't be null", argumentCaptor.getValue().getMessage());
    }

    public void test_ExceptionEmptyBackendRoles() throws IOException, InterruptedException {
        String owner = "owner|IT,HR|myTenant";
        User user = User.parse("owner|IT,HR|myTenant");
        getResponse = modelGroupBuilder(null, AccessMode.RESTRICTED.getValue(), owner);

        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any())).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<Boolean> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        modelAccessControlHelper
            .validateModelGroupAccess(user, mlFeatureEnabledSetting, null, null, "testGroupID", client, sdkClient, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Backend roles shouldn't be null", argumentCaptor.getValue().getMessage());
    }

    // TODO Remove when all calls are migrated to SdkClient version
    public void test_MatchingBackendRoles_NoSdkClient() throws IOException {
        String owner = "owner|IT,HR|myTenant";
        List<String> backendRoles = Arrays.asList("IT", "HR");
        setupModelGroup(owner, AccessMode.RESTRICTED.getValue(), backendRoles);
        User user = User.parse("owner|IT,HR|myTenant");
        modelAccessControlHelper.validateModelGroupAccess(user, "testGroupID", null, client, actionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    public void test_MatchingBackendRoles() throws IOException, InterruptedException {
        String owner = "owner|IT,HR|myTenant";
        List<String> backendRoles = Arrays.asList("IT", "HR");
        setupModelGroup(owner, AccessMode.RESTRICTED.getValue(), backendRoles);
        User user = User.parse("owner|IT,HR|myTenant");

        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any())).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<Boolean> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        modelAccessControlHelper
            .validateModelGroupAccess(user, mlFeatureEnabledSetting, null, "testGroupID", null, client, sdkClient, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    // TODO Remove when all calls are migrated to SdkClient version
    public void test_PublicModelGroup_NoSdkClient() throws IOException {
        String owner = "owner|IT,HR|myTenant";
        List<String> backendRoles = Arrays.asList("IT", "HR");
        setupModelGroup(owner, AccessMode.PUBLIC.getValue(), backendRoles);
        User user = User.parse("owner|IT,HR|myTenant");
        modelAccessControlHelper.validateModelGroupAccess(user, "testGroupID", null, client, actionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    public void test_PublicModelGroup() throws IOException, InterruptedException {
        String owner = "owner|IT,HR|myTenant";
        List<String> backendRoles = Arrays.asList("IT", "HR");
        setupModelGroup(owner, AccessMode.PUBLIC.getValue(), backendRoles);
        User user = User.parse("owner|IT,HR|myTenant");

        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any())).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<Boolean> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        modelAccessControlHelper
            .validateModelGroupAccess(user, mlFeatureEnabledSetting, null, "testGroupID", null, client, sdkClient, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    // TODO Remove when all calls are migrated to SdkClient version
    public void test_PrivateModelGroupWithSameOwner_NoSdkClient() throws IOException {
        String owner = "owner|IT,HR|myTenant";
        List<String> backendRoles = Arrays.asList("IT", "HR");
        setupModelGroup(owner, AccessMode.PRIVATE.getValue(), backendRoles);
        User user = User.parse("owner|IT,HR|myTenant");
        modelAccessControlHelper.validateModelGroupAccess(user, "testGroupID", null, client, actionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    public void test_PrivateModelGroupWithSameOwner() throws IOException, InterruptedException {
        String owner = "owner|IT,HR|myTenant";
        List<String> backendRoles = Arrays.asList("IT", "HR");
        setupModelGroup(owner, AccessMode.PRIVATE.getValue(), backendRoles);
        User user = User.parse("owner|IT,HR|myTenant");

        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any())).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<Boolean> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        modelAccessControlHelper
            .validateModelGroupAccess(user, mlFeatureEnabledSetting, null, "testGroupID", null, client, sdkClient, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue());
    }

    // TODO Remove when all calls are migrated to SdkClient version
    public void test_PrivateModelGroupWithDifferentOwner_NoSdkClient() throws IOException {
        String owner = "owner|IT,HR|myTenant";
        List<String> backendRoles = Arrays.asList("IT", "HR");
        setupModelGroup(owner, AccessMode.PRIVATE.getValue(), backendRoles);
        User user = User.parse("user|IT,HR|myTenant");
        modelAccessControlHelper.validateModelGroupAccess(user, "testGroupID", null, client, actionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertFalse(argumentCaptor.getValue());
    }

    public void test_PrivateModelGroupWithDifferentOwner() throws IOException, InterruptedException {
        String owner = "owner|IT,HR|myTenant";
        List<String> backendRoles = Arrays.asList("IT", "HR");
        setupModelGroup(owner, AccessMode.PRIVATE.getValue(), backendRoles);
        User user = User.parse("user|IT,HR|myTenant");

        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any())).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<Boolean> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        modelAccessControlHelper
            .validateModelGroupAccess(user, mlFeatureEnabledSetting, null, "testGroupID", null, client, sdkClient, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertFalse(argumentCaptor.getValue());
    }

    public void test_SkipModelAccessControl() {
        User admin = User.parse("owner|IT,HR|all_access");
        User user = User.parse("owner|IT,HR|myTenant");
        assertTrue(modelAccessControlHelper.skipModelAccessControl(admin));
        assertFalse(modelAccessControlHelper.skipModelAccessControl(user));
    }

    public void test_IsSecurityEnabled() {
        User user = User.parse("owner|IT,HR|myTenant");
        assertTrue(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(user));
    }

    public void test_IsAdmin() {
        User admin = User.parse("owner|IT,HR|all_access");
        User user = User.parse("owner|IT,HR|");
        assertFalse(modelAccessControlHelper.isAdmin(null));
        assertFalse(modelAccessControlHelper.isAdmin(user));
        assertTrue(modelAccessControlHelper.isAdmin(admin));
    }

    public void test_IsOwner() {
        User owner = User.parse("owner|IT,HR|all_access");
        User user = User.parse("owner|IT,HR|all_access");
        User differentUser = User.parse("user|IT,HR|");
        assertFalse(modelAccessControlHelper.isOwner(null, null));
        assertFalse(modelAccessControlHelper.isOwner(owner, differentUser));
        assertTrue(modelAccessControlHelper.isOwner(owner, user));
    }

    public void test_IsUserHasBackendRole() {
        User user = User.parse("owner|IT,HR|all_access");
        MLModelGroupBuilder builder = MLModelGroup.builder();
        assertTrue(
            modelAccessControlHelper.isUserHasBackendRole(null, builder.name("test_group").access(AccessMode.PUBLIC.getValue()).build())
        );
        assertFalse(
            modelAccessControlHelper.isUserHasBackendRole(null, builder.name("test_group").access(AccessMode.PRIVATE.getValue()).build())
        );
        assertTrue(
            modelAccessControlHelper
                .isUserHasBackendRole(
                    user,
                    builder.access(AccessMode.RESTRICTED.getValue()).backendRoles(Arrays.asList("IT", "HR")).build()
                )
        );
        assertFalse(modelAccessControlHelper.isUserHasBackendRole(user, builder.backendRoles(Arrays.asList("Finance")).build()));
    }

    public void test_IsOwnerStillHasPermission() {
        User owner = User.parse("owner|IT,HR|myTenant");
        User user = User.parse("owner|IT,HR|myTenant");
        User differentUser = User.parse("user|Finance|myTenant");
        User userLostAccess = User.parse("owner|Finance|myTenant");
        assertTrue(modelAccessControlHelper.isOwnerStillHasPermission(null, null));
        MLModelGroupBuilder builder = MLModelGroup.builder();
        assertTrue(
            modelAccessControlHelper
                .isOwnerStillHasPermission(user, builder.name("test_group").access(AccessMode.PUBLIC.getValue()).build())
        );
        assertTrue(
            modelAccessControlHelper
                .isOwnerStillHasPermission(user, builder.name("test_group").access(AccessMode.PRIVATE.getValue()).owner(owner).build())
        );
        assertFalse(
            modelAccessControlHelper
                .isOwnerStillHasPermission(differentUser, builder.access(AccessMode.PRIVATE.getValue()).owner(owner).build())
        );
        assertThrows(
            IllegalStateException.class,
            () -> modelAccessControlHelper.isOwnerStillHasPermission(user, builder.access(AccessMode.RESTRICTED.getValue()).build())
        );
        assertTrue(
            modelAccessControlHelper
                .isOwnerStillHasPermission(
                    user,
                    builder.access(AccessMode.RESTRICTED.getValue()).backendRoles(Arrays.asList("IT", "HR")).build()
                )
        );
        assertFalse(
            modelAccessControlHelper
                .isOwnerStillHasPermission(
                    userLostAccess,
                    builder.access(AccessMode.RESTRICTED.getValue()).backendRoles(Arrays.asList("IT", "HR")).build()
                )
        );
        assertThrows(
            IllegalStateException.class,
            () -> modelAccessControlHelper
                .isOwnerStillHasPermission(user, builder.access(null).backendRoles(Arrays.asList("IT", "HR")).build())
        );
    }

    public void test_AddUserBackendRolesFilter() {
        User user = User.parse("owner|IT,HR|myTenant");
        SearchSourceBuilder builder = new SearchSourceBuilder();
        assertNotNull(modelAccessControlHelper.addUserBackendRolesFilter(user, builder));
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        builder.query(boolQueryBuilder);
        assertNotNull(modelAccessControlHelper.addUserBackendRolesFilter(user, builder));
        builder = new SearchSourceBuilder();
        MatchAllQueryBuilder matchAllQueryBuilder = new MatchAllQueryBuilder();
        builder.query(matchAllQueryBuilder);
        assertNotNull(modelAccessControlHelper.addUserBackendRolesFilter(user, builder));
    }

    public void test_CreateSearchSourceBuilder() {
        User user = User.parse("owner|IT,HR|myTenant");
        assertNotNull(modelAccessControlHelper.createSearchSourceBuilder(user));
    }

    private GetResponse modelGroupBuilder(List<String> backendRoles, String access, String owner) throws IOException {
        MLModelGroup mlModelGroup = MLModelGroup
            .builder()
            .modelGroupId("testModelGroupId")
            .name("testModelGroup")
            .description("This is test model Group")
            .owner(User.parse(owner))
            .backendRoles(backendRoles)
            .access(access)
            .build();
        XContentBuilder content = mlModelGroup.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult(CommonValue.ML_MODEL_GROUP_INDEX, "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

}

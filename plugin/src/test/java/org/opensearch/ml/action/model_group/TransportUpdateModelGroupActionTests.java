/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
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
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

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

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        threadContext = new ThreadContext(settings);
        transportUpdateModelGroupAction = new TransportUpdateModelGroupAction(
            transportService,
            actionFilters,
            client,
            settings,
            sdkClient,
            xContentRegistry,
            clusterService,
            modelAccessControlHelper,
            mlModelGroupManager,
            mlFeatureEnabledSetting,
            mlResourceSharingExtension
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
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        SearchResponse searchResponse = createModelGroupSearchResponse(0);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any(), any());

        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void test_NonOwnerChangingAccessContentException() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Only owner or admin can update access control data.", argumentCaptor.getValue().getMessage());
    }

    public void test_OwnerNoMoreHasPermissionException() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You don't have the specified backend role to update this model group. For more information, contact your administrator.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_NoAccessUserUpdatingModelGroupException() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);
        when(modelAccessControlHelper.isUserHasBackendRole(any(), any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permission to update this model group.", argumentCaptor.getValue().getMessage());
    }

    public void test_BackendRolesProvidedWithPrivate() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isUserHasBackendRole(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PRIVATE, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You can specify backend roles only for a model group with the restricted access mode.", argumentCaptor.getValue().getMessage());
    }

    public void test_BackendRolesProvidedWithPublic() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isUserHasBackendRole(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PUBLIC, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You can specify backend roles only for a model group with the restricted access mode.", argumentCaptor.getValue().getMessage());
    }

    public void test_AdminSpecifiedAddAllBackendRolesForRestricted() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Admin users cannot add all backend roles to a model group.", argumentCaptor.getValue().getMessage());
    }

    public void test_UserWithNoBackendRolesSpecifiedRestricted() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "bob||engineering,operations");
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have any backend roles.", argumentCaptor.getValue().getMessage());
    }

    public void test_UserSpecifiedRestrictedButNoBackendRolesField() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, false);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You have to specify backend roles when add all backend roles is set to false.", argumentCaptor.getValue().getMessage());
    }

    public void test_RestrictedAndUserSpecifiedBothBackendRolesFields() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(backendRoles, AccessMode.RESTRICTED, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You cannot specify backend roles and add all backend roles at the same time.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_RestrictedAndUserSpecifiedIncorrectBackendRoles() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        List<String> incorrectBackendRole = Arrays.asList("Finance");

        MLUpdateModelGroupRequest actionRequest = prepareRequest(incorrectBackendRole, AccessMode.RESTRICTED, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have the backend roles specified.", argumentCaptor.getValue().getMessage());
    }

    public void test_SuccessPrivateWithOwnerAsUser() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PRIVATE, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_SuccessRestricedWithOwnerAsUser() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "bob|IT,HR|myTenant");
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_SuccessPublicWithAdminAsUser() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isOwnerStillHasPermission(any(), any())).thenReturn(true);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PUBLIC, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_SuccessRestrictedWithAdminAsUser() {
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(backendRoles, AccessMode.RESTRICTED, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_SuccessNonOwnerUpdatingWithNoAccessContent() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);
        when(modelAccessControlHelper.isOwner(any(), any())).thenReturn(false);
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(false);
        when(modelAccessControlHelper.isUserHasBackendRole(any(), any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_FailedToFindModelGroupException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new MLResourceNotFoundException("Failed to find model group"));
            return null;
        }).when(client).get(any(), any());

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get data object from index .plugins-ml-model-group", argumentCaptor.getValue().getMessage());
    }

    public void test_FailedToGetModelGroupException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new Exception("Failed to get model group"));
            return null;
        }).when(client).get(any(), any());

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get data object from index .plugins-ml-model-group", argumentCaptor.getValue().getMessage());
    }

    public void test_ModelGroupIndexNotFoundException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new IndexNotFoundException("Fail to find model group"));
            return null;
        }).when(client).get(any(), any());

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.RESTRICTED, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model group", argumentCaptor.getValue().getMessage());
    }

    public void test_FailedToUpdatetModelGroupException() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new MLException("Failed to update Model Group"));
            return null;
        }).when(client).update(any(), any());

        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, AccessMode.PUBLIC, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update Model Group", argumentCaptor.getValue().getMessage());
    }

    public void test_SuccessSecurityDisabledCluster() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLUpdateModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_ModelGroupNameNotUnique() throws IOException {

        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        SearchResponse searchResponse = createModelGroupSearchResponse(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any(), any());

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, null);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "The name you provided is already being used by another model with ID: model_group_ID. Please provide a different name",
                argumentCaptor.getValue().getMessage()
        );
    }

    public void test_ExceptionSecurityDisabledCluster() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLUpdateModelGroupRequest actionRequest = prepareRequest(null, null, true);
        transportUpdateModelGroupAction.doExecute(task, actionRequest, actionListener);
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

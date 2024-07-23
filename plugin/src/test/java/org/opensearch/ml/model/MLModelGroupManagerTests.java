/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

public class MLModelGroupManagerTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private ActionListener<String> actionListener;

    @Mock
    private ActionListener<GetResponse> modelGroupListener;

    @Mock
    private IndexResponse indexResponse;

    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private MLModelGroupManager mlModelGroupManager;

    private final List<String> backendRoles = Arrays.asList("IT", "HR");

    @Before
    public void setup() throws IOException {
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

        SearchResponse searchResponse = createModelGroupSearchResponse(0);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

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

    public void test_ModelGroupNameNotUnique() throws IOException {//
        SearchResponse searchResponse = createModelGroupSearchResponse(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);

        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "The name you provided is already being used by a model group with ID: model_group_ID.",
            argumentCaptor.getValue().getMessage()
        );

    }

    public void test_SuccessPublic() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.PUBLIC, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_DefaultPrivateModelGroup() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
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

    public void test_ProvidedBothBackendRolesAndAddAllBackendRolesWithNoAccessMode() {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(backendRoles, null, true);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You cannot specify backend roles and add all backend roles at the same time.", argumentCaptor.getValue().getMessage());
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

    public void test_SuccessGetModelGroup() throws IOException {
        MLModelGroup modelGroup = MLModelGroup
            .builder()
            .modelGroupId("testModelGroupID")
            .name("test")
            .description("this is test group")
            .latestVersion(1)
            .backendRoles(Arrays.asList("role1", "role2"))
            .owner(new User())
            .access(AccessMode.PUBLIC.name())
            .build();

        GetResponse getResponse = prepareGetResponse(modelGroup);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        mlModelGroupManager.getModelGroupResponse("testModelGroupID", modelGroupListener);
        verify(modelGroupListener).onResponse(getResponse);
    }

    public void test_OtherExceptionGetModelGroup() throws IOException {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener
                .onFailure(
                    new RuntimeException("Any other Exception occurred during getting the model group. Please check log for more details.")
                );
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        mlModelGroupManager.getModelGroupResponse("testModelGroupID", modelGroupListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(modelGroupListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Any other Exception occurred during getting the model group. Please check log for more details.",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_NotFoundGetModelGroup() throws IOException {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        mlModelGroupManager.getModelGroupResponse("testModelGroupID", modelGroupListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(modelGroupListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model group with ID: testModelGroupID", argumentCaptor.getValue().getMessage());
    }

    public void test_NoResponseoInitModelGroup() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initModelGroupIndexIfAbsent(any());

        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("No response to create ML Model Group index", argumentCaptor.getValue().getMessage());
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

    private GetResponse prepareGetResponse(MLModelGroup mlModelGroup) throws IOException {
        XContentBuilder content = mlModelGroup.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}

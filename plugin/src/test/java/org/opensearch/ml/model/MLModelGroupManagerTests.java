/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.TotalHits;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.PlainActionFuture;
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
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class MLModelGroupManagerTests extends OpenSearchTestCase {
    private static final String TENANT_ID = "tenant_id";

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

    SdkClient sdkClient;

    @Mock
    private ActionListener<String> actionListener;

    @Mock
    private ActionListener<GetResponse> modelGroupListener;

    private IndexResponse indexResponse;

    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private MLModelGroupManager mlModelGroupManager;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Captor
    ArgumentCaptor<PutDataObjectRequest> putDataObjectRequestArgumentCaptor;

    private final List<String> backendRoles = Arrays.asList("IT", "HR");

    private static TestThreadPool testThreadPool = new TestThreadPool(
        MLModelGroupManagerTests.class.getName(),
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

        sdkClient = Mockito.spy(new LocalClusterIndicesClient(client, xContentRegistry));
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        mlModelGroupManager = new MLModelGroupManager(
            mlIndicesHandler,
            client,
            sdkClient,
            clusterService,
            modelAccessControlHelper,
            mlFeatureEnabledSetting
        );
        assertNotNull(mlModelGroupManager);

        indexResponse = new IndexResponse(new ShardId(ML_MODEL_GROUP_INDEX, "_na_", 0), "model_group_ID", 1, 0, 2, true);

        PlainActionFuture<IndexResponse> future = PlainActionFuture.newFuture();
        future.onResponse(indexResponse);
        when(client.index(any(IndexRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initModelGroupIndexIfAbsent(any());

        SearchResponse searchResponse = getEmptySearchResponse();

        PlainActionFuture<SearchResponse> searchFuture = PlainActionFuture.newFuture();
        searchFuture.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(searchFuture);

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(any())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void test_SuccessAddAllBackendRolesTrue() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_ModelGroupNameNotUnique() throws IOException, InterruptedException {//
        SearchResponse searchResponse = getNonEmptySearchResponse();
        PlainActionFuture<SearchResponse> searchFuture = PlainActionFuture.newFuture();
        searchFuture.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(searchFuture);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "The name you provided is already being used by a model group with ID: model_group_ID.",
            argumentCaptor.getValue().getMessage()
        );

    }

    @Test
    public void test_SuccessPublic() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.PUBLIC, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        verify(sdkClient).putDataObjectAsync(putDataObjectRequestArgumentCaptor.capture(), Mockito.any());
        Assert.assertEquals(TENANT_ID, putDataObjectRequestArgumentCaptor.getValue().tenantId());
    }

    @Test
    public void test_DefaultPrivateModelGroup() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_ModelAccessModeNullAddAllBackendRolesTrue() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_ModelAccessModeNullAddAllBackendRolesFalse() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT, HR|engineering, operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, false);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_BackendRolesProvidedWithPublic() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.PUBLIC, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You can specify backend roles only for a model group with the restricted access mode.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_ProvidedBothBackendRolesAndAddAllBackendRolesWithNoAccessMode() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(backendRoles, null, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You cannot specify backend roles and add all backend roles at the same time.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_BackendRolesProvidedWithPrivate() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.PRIVATE, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You can specify backend roles only for a model group with the restricted access mode.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_AdminSpecifiedAddAllBackendRolesForRestricted() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "admin|admin|all_access");
        when(modelAccessControlHelper.isAdmin(any())).thenReturn(true);
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.RESTRICTED, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Admin users cannot add all backend roles to a model group.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_UserWithNoBackendRolesSpecifiedRestricted() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex||engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.RESTRICTED, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You must have at least one backend role to register a restricted model group.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void test_UserSpecifiedRestrictedButNoBackendRolesFieldF() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, AccessMode.RESTRICTED, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You must specify one or more backend roles or add all backend roles to register a restricted model group.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void test_RestrictedAndUserSpecifiedBothBackendRolesField() throws InterruptedException {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(backendRoles, AccessMode.RESTRICTED, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
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
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        List<String> incorrectBackendRole = Arrays.asList("Finance");

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(incorrectBackendRole, AccessMode.RESTRICTED, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have the backend roles specified.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_SuccessSecurityDisabledCluster() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_ExceptionSecurityDisabledCluster() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster.",
                argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void test_ExceptionFailedToInitModelGroupIndex() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(true);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    @Test
    public void test_ExceptionFailedToIndexModelGroup() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);
        PlainActionFuture<IndexResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new Exception("Index Not Found"));
        when(client.index(any(IndexRequest.class))).thenReturn(future);

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Index Not Found", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_ExceptionInitModelGroupIndexIfAbsent() throws InterruptedException {
        when(modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(any())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onFailure(new Exception("Index Not Found"));
            return null;
        }).when(mlIndicesHandler).initModelGroupIndexIfAbsent(any());

        MLRegisterModelGroupInput mlRegisterModelGroupInput = prepareRequest(null, null, null);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        mlModelGroupManager.createModelGroup(mlRegisterModelGroupInput, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Index Not Found", argumentCaptor.getValue().getMessage());
    }

    @Test
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

    @Test
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

    @Test
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

    private MLRegisterModelGroupInput prepareRequest(List<String> backendRoles, AccessMode modelAccessMode, Boolean isAddAllBackendRoles) {
        return MLRegisterModelGroupInput
            .builder()
            .name("modelGroupName")
            .description("This is a test model group")
            .backendRoles(backendRoles)
            .modelAccessMode(modelAccessMode)
            .isAddAllBackendRoles(isAddAllBackendRoles)
            .tenantId(TENANT_ID)
            .build();
    }

    private SearchResponse getEmptySearchResponse() {
        SearchHits hits = new SearchHits(new SearchHit[0], null, Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, true, false, null, 1);
        SearchResponse searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
        return searchResponse;
    }

    private SearchResponse getNonEmptySearchResponse() throws IOException {
        SearchHit[] hits = new SearchHit[1];
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"access\": \"public\",\n"
            + "                    \"latest_version\": 0,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"_id\": \"model_group_ID\",\n"
            + "                    \"name\": \"model_group_IT\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit model = SearchHit.fromXContent(TestHelper.parser(modelContent));
        hits[0] = model;
        SearchHits searchHits = new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponseSections searchSections = new SearchResponseSections(
            searchHits,
            InternalAggregations.EMPTY,
            null,
            true,
            false,
            null,
            1
        );
        SearchResponse searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
        return searchResponse;
    }

    private GetResponse prepareGetResponse(MLModelGroup mlModelGroup) throws IOException {
        XContentBuilder content = mlModelGroup.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}

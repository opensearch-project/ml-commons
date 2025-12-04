/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.ALL;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.FIELD_DESCRIPTION;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.LOG_RELATED_INDEX_CHECK;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.STATISTICAL_DATA;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.indexInsight.FieldDescriptionTask;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightTask;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.indexInsight.LogRelatedIndexCheckTask;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.indexInsight.StatisticalDataTask;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class GetIndexInsightTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    AdminClient adminClient;

    @Mock
    IndicesAdminClient indicesAdminClient;

    @Mock
    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionListener<MLIndexInsightGetResponse> actionListener;

    @Mock
    ActionFilters actionFilters;

    @Mock
    GetMappingsResponse getMappingsResponse;

    @Mock
    MappingMetadata mappingMetadata;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    GetIndexInsightTransportAction getIndexInsightTransportAction;
    MLIndexInsightGetRequest mlIndexInsightGetRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlIndexInsightGetRequest = MLIndexInsightGetRequest
            .builder()
            .indexName("test_index_name")
            .targetIndexInsight(STATISTICAL_DATA)
            .tenantId(null)
            .build();

        getIndexInsightTransportAction = spy(
            new GetIndexInsightTransportAction(
                transportService,
                actionFilters,
                xContentRegistry,
                mlFeatureEnabledSetting,
                client,
                sdkClient,
                mlIndicesHandler
            )
        );

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(), any());
        when(mlFeatureEnabledSetting.isIndexInsightEnabled()).thenReturn(Boolean.TRUE);
        when(getMappingsResponse.getMappings()).thenReturn(Map.of("demo", mappingMetadata));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());

    }

    @Test
    public void testGetIndexInsight_Successful() {
        setupMockResponses();

        IndexInsightTask indexInsightTask = mock(IndexInsightTask.class);
        doReturn(indexInsightTask).when(getIndexInsightTransportAction).createTask(any());

        IndexInsight insight = new IndexInsight(
            "test_index",
            "test content",
            IndexInsightTaskStatus.COMPLETED,
            STATISTICAL_DATA,
            Instant.ofEpochMilli(0),
            ""
        );
        doAnswer(invocation -> {
            ActionListener<IndexInsight> listener = invocation.getArgument(1);
            listener.onResponse(insight);
            return null;
        }).when(indexInsightTask).execute(any(), any());

        getIndexInsightTransportAction.doExecute(null, mlIndexInsightGetRequest, actionListener);
        ArgumentCaptor<MLIndexInsightGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLIndexInsightGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals("test_index", argumentCaptor.getValue().getIndexInsight().getIndex());
    }

    @Test
    public void testGetIndexInsight_FailToAccess() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"is_enable\":true}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);
        IndexInsightTask indexInsightTask = mock(IndexInsightTask.class);

        doReturn(indexInsightTask).when(getIndexInsightTransportAction).createTask(any());
        IndexInsight insight = new IndexInsight(
            "test_index",
            "test content",
            IndexInsightTaskStatus.COMPLETED,
            STATISTICAL_DATA,
            Instant.ofEpochMilli(0),
            ""
        );
        doAnswer(invocation -> {
            ActionListener<IndexInsight> listener = invocation.getArgument(1);
            listener.onResponse(insight);
            return null;
        }).when(indexInsightTask).execute(any(), any());

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IllegalArgumentException("no permissions"));
            return null;
        }).when(client).search(any(), any());

        getIndexInsightTransportAction.doExecute(null, mlIndexInsightGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof IllegalArgumentException);
        assertEquals("no permissions", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testCreateTask() {
        MLIndexInsightGetRequest statisticalRequest = new MLIndexInsightGetRequest("test_index", STATISTICAL_DATA, null);
        IndexInsightTask statisticalTask = getIndexInsightTransportAction.createTask(statisticalRequest);
        assertNotNull(statisticalTask);
        assertTrue(statisticalTask instanceof StatisticalDataTask);

        MLIndexInsightGetRequest fieldRequest = new MLIndexInsightGetRequest("test_index", FIELD_DESCRIPTION, null);
        IndexInsightTask fieldTask = getIndexInsightTransportAction.createTask(fieldRequest);
        assertNotNull(fieldTask);
        assertTrue(fieldTask instanceof FieldDescriptionTask);

        MLIndexInsightGetRequest logRequest = new MLIndexInsightGetRequest("test_index", LOG_RELATED_INDEX_CHECK, null);
        IndexInsightTask logTask = getIndexInsightTransportAction.createTask(logRequest);
        assertNotNull(logTask);
        assertTrue(logTask instanceof LogRelatedIndexCheckTask);
    }

    @Test
    public void testGetIndexInsight_FailToGetObject() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"is_enable\":true}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.failedFuture(new RuntimeException("Fail to get data object."));

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        SearchResponse searchResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        getIndexInsightTransportAction.doExecute(null, mlIndexInsightGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to get data object.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetIndexInsightConfig_FailToMultiTenant() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        getIndexInsightTransportAction.doExecute(null, mlIndexInsightGetRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof RuntimeException);
        assertEquals("You don't have permission to access this resource", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetIndexInsight_ALLType_Successful() {
        testGetIndexInsight_ALLType(null, Map.of("STATISTICAL_DATA", true, "FIELD_DESCRIPTION", true, "LOG_RELATED_INDEX_CHECK", true));
    }

    @Test
    public void testGetIndexInsight_ALLType_StatisticalDataFailed() {
        testGetIndexInsight_ALLType(
            STATISTICAL_DATA,
            Map.of("STATISTICAL_DATA", false, "FIELD_DESCRIPTION", false, "LOG_RELATED_INDEX_CHECK", true)
        );
    }

    @Test
    public void testGetIndexInsight_ALLType_FieldDescriptionFailed() {
        testGetIndexInsight_ALLType(
            FIELD_DESCRIPTION,
            Map.of("STATISTICAL_DATA", true, "FIELD_DESCRIPTION", false, "LOG_RELATED_INDEX_CHECK", true)
        );
    }

    @Test
    public void testGetIndexInsight_ALLType_LogRelatedIndexCheckFailed() {
        testGetIndexInsight_ALLType(
            LOG_RELATED_INDEX_CHECK,
            Map.of("STATISTICAL_DATA", true, "FIELD_DESCRIPTION", true, "LOG_RELATED_INDEX_CHECK", false)
        );
    }

    private void testGetIndexInsight_ALLType(MLIndexInsightType failedType, Map<String, Boolean> expectedContent) {
        setupMockResponses();

        Map<MLIndexInsightType, String> contentMap = Map
            .of(STATISTICAL_DATA, "stats content", FIELD_DESCRIPTION, "field content", LOG_RELATED_INDEX_CHECK, "log content");

        doAnswer(invocation -> {
            MLIndexInsightGetRequest request = invocation.getArgument(0);
            IndexInsightTask task = mock(IndexInsightTask.class);
            MLIndexInsightType taskType = request.getTargetIndexInsight();

            doAnswer(taskInvocation -> {
                ActionListener<IndexInsight> listener = taskInvocation.getArgument(1);

                if (taskType.equals(failedType)
                    || (taskType.equals(FIELD_DESCRIPTION) && failedType != null && failedType.equals(STATISTICAL_DATA))) {
                    listener.onFailure(new RuntimeException("Task failed"));
                } else {
                    String content = contentMap.get(taskType);
                    IndexInsight insight = new IndexInsight(
                        "test_index",
                        content,
                        IndexInsightTaskStatus.COMPLETED,
                        taskType,
                        Instant.now(),
                        ""
                    );
                    listener.onResponse(insight);
                }
                return null;
            }).when(task).execute(any(), any());

            return task;
        }).when(getIndexInsightTransportAction).createTask(any());

        MLIndexInsightGetRequest allTypeRequest = MLIndexInsightGetRequest
            .builder()
            .indexName("test_index_name")
            .targetIndexInsight(ALL)
            .tenantId(null)
            .build();

        getIndexInsightTransportAction.doExecute(null, allTypeRequest, actionListener);

        ArgumentCaptor<MLIndexInsightGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLIndexInsightGetResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());

        MLIndexInsightGetResponse response = argumentCaptor.getValue();
        IndexInsight result = response.getIndexInsight();

        assertEquals("test_index_name", result.getIndex());
        assertEquals(ALL, result.getTaskType());

        String content = result.getContent();
        expectedContent.forEach((type, shouldContain) -> {
            if (shouldContain) {
                String expectedValue = contentMap.get(MLIndexInsightType.valueOf(type));
                assertTrue(content.contains(type + ":\n" + expectedValue));
            } else {
                assertFalse(content.contains(type));
            }
        });
    }

    private void setupMockResponses() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"is_enable\":true}");

        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);
        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        SearchResponse searchResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
    }

}

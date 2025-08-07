/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.BaseModelConfig.FrameworkType;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportRegisterModelMetaActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLModelManager mlModelManager;
    @Mock
    private MLModelGroupManager mlModelGroupManager;

    @Mock
    private ActionListener<MLRegisterModelMetaResponse> actionListener;

    @Mock
    private Task task;

    @Mock
    private ThreadPool threadPool;

    ThreadContext threadContext;

    private TransportRegisterModelMetaAction action;

    @Mock
    private Client client;
    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        action = new TransportRegisterModelMetaAction(
            transportService,
            actionFilters,
            mlModelManager,
            client,
            modelAccessControlHelper,
            mlModelGroupManager
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("customModelId");
            return null;
        }).when(mlModelManager).registerModelMeta(any(), any());

        SearchResponse searchResponse = createModelGroupSearchResponse(0);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any(), any());

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testTransportRegisterModelMetaActionConstructor() {
        assertNotNull(action);
    }

    @Test
    public void testTransportRegisterModelMetaActionDoExecute() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        MLRegisterModelMetaRequest actionRequest = prepareRequest("modelGroupID");
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelMetaResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelMetaResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void testDoExecute_successWithCreateModelGroup() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("modelGroupID");
            return null;
        }).when(mlModelGroupManager).createModelGroup(any(), any());

        MLRegisterModelMetaRequest actionRequest = prepareRequest(null);
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelMetaResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelMetaResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void testDoExecute_failureWithCreateModelGroup() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Failed to create Model Group"));
            return null;
        }).when(mlModelGroupManager).createModelGroup(any(), any());

        MLRegisterModelMetaRequest actionRequest = prepareRequest(null);
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to create Model Group", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_userHasNoAccessException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        MLRegisterModelMetaRequest actionRequest = prepareRequest("modelGroupID");
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permissions to perform this operation on this model.", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_ValidationFailedException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        MLRegisterModelMetaRequest actionRequest = prepareRequest("modelGroupID");
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_ModelNameAlreadyExists() throws IOException {

        SearchResponse searchResponse = createModelGroupSearchResponse(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any(), any());

        MLRegisterModelMetaRequest actionRequest = prepareRequest(null);
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelMetaResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelMetaResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void testDoExecute_NoAccessWhenModelNameAlreadyExists() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any());

        SearchResponse searchResponse = createModelGroupSearchResponse(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any(), any());

        MLRegisterModelMetaRequest actionRequest = prepareRequest(null);
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "The name {Test Model} you provided is unavailable because it is used by another model group with id {model_group_ID} to which you do not have access. Please provide a different name.",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void test_FailureWhenSearchingModelGroupName() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Runtime exception"));
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any(), any());

        MLRegisterModelMetaRequest actionRequest = prepareRequest(null);
        action.doExecute(task, actionRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Runtime exception", argumentCaptor.getValue().getMessage());
    }

    private MLRegisterModelMetaRequest prepareRequest(String modelGroupID) {
        MLRegisterModelMetaInput input = MLRegisterModelMetaInput
            .builder()
            .name("Test Model")
            .modelGroupId(modelGroupID)
            .description("Custom Model Test")
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .functionName(FunctionName.BATCH_RCF)
            .modelContentHashValue("14555")
            .modelContentSizeInBytes(1000L)
            .modelConfig(
                new TextEmbeddingModelConfig(
                    "CUSTOM",
                    123,
                    FrameworkType.SENTENCE_TRANSFORMERS,
                    "all config",
                    null,
                    TextEmbeddingModelConfig.PoolingMode.MEAN,
                    true,
                    512
                )
            )
            .totalChunks(2)
            .build();
        return new MLRegisterModelMetaRequest(input);
    }

    private SearchResponse createModelGroupSearchResponse(long totalHits) throws IOException {

        SearchResponse searchResponse = mock(SearchResponse.class);
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"access\": \"public\",\n"
            + "                    \"latest_version\": 0,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"_id\": \"model_group_ID\",\n"
            + "                    \"name\": \"Test Model\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit modelGroup = SearchHit.fromXContent(TestHelper.parser(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { modelGroup }, new TotalHits(totalHits, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }

}

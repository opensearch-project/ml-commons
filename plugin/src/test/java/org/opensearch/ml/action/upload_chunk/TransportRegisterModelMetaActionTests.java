/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.core.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig.FrameworkType;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportRegisterModelMetaActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLModelManager mlModelManager;

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
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        action = new TransportRegisterModelMetaAction(transportService, actionFilters, mlModelManager, client, modelAccessControlHelper);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("customModelId");
            return null;
        }).when(mlModelManager).registerModelMeta(any(), any());

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testTransportRegisterModelMetaActionConstructor() {
        assertNotNull(action);
    }

    public void testTransportRegisterModelMetaActionDoExecute() {
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        MLRegisterModelMetaRequest actionRequest = prepareRequest();
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<MLRegisterModelMetaResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelMetaResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testDoExecute_userHasNoAccessException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        MLRegisterModelMetaRequest actionRequest = prepareRequest();
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have valid privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void test_ValidationFailedException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        MLRegisterModelMetaRequest actionRequest = prepareRequest();
        action.doExecute(task, actionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    private MLRegisterModelMetaRequest prepareRequest() {
        MLRegisterModelMetaInput input = MLRegisterModelMetaInput
            .builder()
            .name("Model Name")
            .modelGroupId("1")
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
                    TextEmbeddingModelConfig.PoolingMode.MEAN,
                    true,
                    512
                )
            )
            .totalChunks(2)
            .build();
        return new MLRegisterModelMetaRequest(input);
    }

}

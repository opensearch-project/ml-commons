/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportUpdateModelActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    Task task;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<UpdateResponse> actionListener;

    @Mock
    UpdateResponse updateResponse;

    @Mock
    GetResponse getResponse;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    ClusterService clusterService;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    TransportUpdateModelAction transportUpdateModelAction;
    MLUpdateModelRequest mlUpdateModelRequest;
    MLUpdateModelInput mlUpdateModelInput;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        mlUpdateModelInput = MLUpdateModelInput.builder().modelId("test_id").description("testDescription").build();
        mlUpdateModelRequest = MLUpdateModelRequest.builder().updateModelInput(mlUpdateModelInput).build();

        Settings settings = Settings.builder().build();

        transportUpdateModelAction = spy(
            new TransportUpdateModelAction(transportService, actionFilters, client, xContentRegistry, modelAccessControlHelper)
        );

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testUpdateModel_Success() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        GetResponse getResponse = prepareMLModel();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), isA(ActionListener.class));

        transportUpdateModelAction.doExecute(task, mlUpdateModelRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    public GetResponse prepareMLModel() throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .modelId("test_id")
            .description("test_description")
            .modelState(MLModelState.REGISTERED)
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .build();
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}

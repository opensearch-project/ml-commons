/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.MLTaskState.COMPLETED;
import static org.opensearch.ml.common.indexInsight.IndexInsight.CONTENT_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.LAST_UPDATE_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.STATUS_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.TASK_TYPE_FIELD;
import static org.opensearch.ml.common.indexInsight.MLIndexInsightType.FIELD_DESCRIPTION;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.transport.client.Client;

/**
 * Utility class for common mock setups in IndexInsight tests
 */
public class IndexInsightTestHelper {

    public static void mockMLConfigSuccess(Client client) {
        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> configListener = invocation.getArgument(2);
            MLConfig config = MLConfig.builder().type("test").configuration(Configuration.builder().agentId("agent-id").build()).build();
            configListener.onResponse(new MLConfigGetResponse(config));
            return null;
        }).when(client).execute(eq(MLConfigGetAction.INSTANCE), any(MLConfigGetRequest.class), any(ActionListener.class));
    }

    public static void mockMLConfigFailure(Client client, String errorMessage) {
        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> configListener = invocation.getArgument(2);
            configListener.onFailure(new IllegalStateException(errorMessage));
            return null;
        }).when(client).execute(eq(MLConfigGetAction.INSTANCE), any(MLConfigGetRequest.class), any(ActionListener.class));
    }

    public static void mockMLExecuteSuccess(Client client, String response) {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> executeListener = invocation.getArgument(2);
            ModelTensor tensor = ModelTensor.builder().result(response).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            executeListener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build());
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any(ActionListener.class));
    }

    public static void mockMLExecuteFailure(Client client, String errorMessage) {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> executeListener = invocation.getArgument(2);
            executeListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any(ActionListener.class));
    }

    public static void mockUpdateSuccess(SdkClient sdkClient) {
        IndexResponse IndexResponse = mock(IndexResponse.class);
        when(IndexResponse.getResult()).thenReturn(DocWriteResponse.Result.UPDATED);

        PutDataObjectResponse sdkResponse = mock(PutDataObjectResponse.class);
        when(sdkResponse.indexResponse()).thenReturn(IndexResponse);

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.putDataObjectAsync(any())).thenReturn(future);

    }

    public static void mockGetSuccess(SdkClient sdkClient, String content) {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsMap()).thenReturn(Map.of(IndexInsight.CONTENT_FIELD, content));
        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);
    }

    public static void mockGetSuccess(SdkClient sdkClient, Map<String, Object> content) {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsMap()).thenReturn(content);
        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);
    }

    public static void mockGetFailToGet(SdkClient sdkClient, String content) {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        when(getResponse.getSourceAsMap()).thenReturn(Map.of(IndexInsight.CONTENT_FIELD, content));
        GetDataObjectResponse sdkResponse = mock(GetDataObjectResponse.class);
        when(sdkResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);
    }

    public static void mockSearchSuccess(SdkClient sdkClient) throws IOException {
        SearchResponse searchResponse = mock(SearchResponse.class);
        SearchHit searchHit = new SearchHit(0, "id", null, null).sourceRef(BytesReference.bytes(buildSampleDoc()));
        SearchHit[] searchHits1 = List.of(searchHit).toArray(new SearchHit[0]);
        SearchHits searchHits = new SearchHits(searchHits1, null, 0, null, null, null);
        when(searchResponse.getHits()).thenReturn(searchHits);
        // when(searchHits.getHits()).thenReturn(searchHits1);
        SearchDataObjectResponse sdkResponse = mock(SearchDataObjectResponse.class);
        when(sdkResponse.searchResponse()).thenReturn(searchResponse);

        CompletableFuture<SearchDataObjectResponse> future = CompletableFuture.completedFuture(sdkResponse);

        when(sdkClient.searchDataObjectAsync(any())).thenReturn(future);
    }

    private static XContentBuilder buildSampleDoc() throws IOException {
        XContentBuilder content = XContentBuilder.builder(XContentType.JSON.xContent());
        content.startObject();
        content.field(INDEX_NAME_FIELD, "test");
        content.field(TASK_TYPE_FIELD, FIELD_DESCRIPTION.toString());
        content.field(CONTENT_FIELD, "test_Content");
        content.field(LAST_UPDATE_FIELD, Instant.now().toEpochMilli());
        content.field(TENANT_ID_FIELD, "tenant-id");
        content.field(STATUS_FIELD, COMPLETED);
        content.endObject();
        return content;
    }

}

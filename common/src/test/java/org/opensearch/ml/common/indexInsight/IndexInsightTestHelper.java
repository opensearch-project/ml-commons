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

import java.util.List;
import java.util.Map;

import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
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

    public static void mockUpdateSuccess(Client client) {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> updateListener = invocation.getArgument(1);
            UpdateResponse updateResponse = mock(UpdateResponse.class);
            updateListener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), any(ActionListener.class));
    }

    public static void mockGetSuccess(Client client, String content) {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(true);
            Map<String, Object> sourceMap = Map.of(IndexInsight.CONTENT_FIELD, content);
            when(response.getSourceAsMap()).thenReturn(sourceMap);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(GetRequest.class), any(ActionListener.class));
    }
}

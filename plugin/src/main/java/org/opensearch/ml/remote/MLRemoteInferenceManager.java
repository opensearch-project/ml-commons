/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.remote;

import org.opensearch.action.ActionListener;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.remote.RemoteModelOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.task.MLTaskManager;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

public class MLRemoteInferenceManager {

    final MLTaskManager mlTaskManager;

    public MLRemoteInferenceManager(MLTaskManager mlTaskManager) {
        this.mlTaskManager = mlTaskManager;
    }

    public void inference(MLTask mlTask, MLModel mlModel, MLInput mlInput, ActionListener<MLTaskResponse> listener) {

        mlTaskManager.updateTaskStateAsRunning(mlTask.getTaskId(), mlTask.isAsync());

        AwsBasicCredentials awsCreds = AwsBasicCredentials.create("access_key", "secret_key");

        SageMakerRuntimeClient sageMakerRuntimeClient = SageMakerRuntimeClient
            .builder()
            .region(Region.US_WEST_2)
            .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
            .build();

        // region loaded from the DefaultAwsRegionProviderChain and credentials loaded from the DefaultCredentialsProvider
        // include access key and secret key in ~/.aws/credentials

        InvokeEndpointRequest invokeEndpointRequest = InvokeEndpointRequest
            .builder()
            .endpointName("endpoint")
            .contentType("text/csv")
            .body(SdkBytes.fromUtf8String(mlModel.getDataTransformer().transform(mlInput)))
            .build();
        // todo: load inputs from MLInput

        InvokeEndpointResponse invokeEndpointResponse = sageMakerRuntimeClient.invokeEndpoint(invokeEndpointRequest);
        RemoteModelOutput remoteModelOutput = RemoteModelOutput
            .builder()
            .taskId(mlTask.getTaskId())
            .predictionResult(invokeEndpointResponse.body().asUtf8String())
            .build();

        MLTaskResponse response = MLTaskResponse.builder().output(remoteModelOutput).build();
        listener.onResponse(response);
    }
}

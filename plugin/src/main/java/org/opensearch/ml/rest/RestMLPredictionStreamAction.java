/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.plugin.MachineLearningPlugin.STREAM_PREDICT_THREAD_POOL;
import static org.opensearch.ml.utils.MLExceptionUtils.BATCH_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.LOCAL_MODEL_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.STREAM_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.getActionTypeFromRestRequest;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.lease.Releasable;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.support.XContentHttpChunk;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.http.HttpChunk;
import org.opensearch.ml.action.prediction.TransportPredictionStreamTaskAction;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionStreamTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.StreamTransportResponseHandler;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportRequestOptions;
import org.opensearch.transport.client.node.NodeClient;
import org.opensearch.transport.stream.StreamTransportResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
public class RestMLPredictionStreamAction extends BaseRestHandler {

    private static final String ML_PREDICTION_STREAM_ACTION = "ml_prediction_stream_action";

    private MLModelManager modelManager;

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private ClusterService clusterService;

    /**
     * Constructor
     */
    public RestMLPredictionStreamAction(
        MLModelManager modelManager,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ClusterService clusterService
    ) {
        this.modelManager = modelManager;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.clusterService = clusterService;
    }

    @Override
    public String getName() {
        return ML_PREDICTION_STREAM_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/{%s}/_predict/stream", ML_BASE_URI, PARAMETER_MODEL_ID)
                )
            );
    }

    @Override
    public boolean supportsContentStream() {
        return true;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean allowsUnsafeBuffers() {
        return true;
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isStreamEnabled()) {
            throw new IllegalStateException(STREAM_DISABLED_ERR_MSG);
        }

        String userAlgorithm = request.param(PARAMETER_ALGORITHM);
        String modelId = getParameterId(request, PARAMETER_MODEL_ID);
        Optional<FunctionName> functionName = modelManager.getOptionalModelFunctionName(modelId);

        // If model not in cache, validate it exists before start streaming
        if (!functionName.isPresent()) {
            validateModelExists(modelId, request);
        }

        return channel -> {
            StreamingRestChannel streamingChannel = (StreamingRestChannel) channel;

            // Set streaming headers
            Map<String, List<String>> headers = Map
                .of(
                    "Content-Type",
                    List.of("text/event-stream"),
                    "Cache-Control",
                    List.of("no-cache"),
                    "Connection",
                    List.of("keep-alive")
                );
            streamingChannel.prepareResponse(RestStatus.OK, headers);

            // Use Flux with cache check
            Flux.from(streamingChannel).ofType(HttpChunk.class).concatMap(chunk -> {
                final CompletableFuture<HttpChunk> future = new CompletableFuture<>();
                try {
                    BytesReference chunkContent = chunk.content();

                    // Check if model is in cache
                    if (functionName.isPresent()) {
                        MLPredictionTaskRequest taskRequest = getRequest(modelId, functionName.get().name(), request, chunkContent);
                        executeStreamingRequest(client, taskRequest, streamingChannel, future);
                    } else {
                        // Model not in cache - load it first
                        loadModelAndExecuteStreaming(client, modelId, userAlgorithm, request, chunkContent, streamingChannel, future);
                    }

                } catch (IOException e) {
                    future.completeExceptionally(e);
                }

                return Mono.fromCompletionStage(future);
            }).doOnNext(streamingChannel::sendChunk).onErrorComplete(ex -> {
                // Error handling
                try {
                    streamingChannel.sendResponse(new BytesRestResponse(streamingChannel, (Exception) ex));
                    return true;
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).subscribe();
        };
    }

    private void validateModelExists(String modelId, RestRequest request) throws IOException {
        try {
            CompletableFuture<MLModel> future = new CompletableFuture<>();

            modelManager
                .getModel(
                    modelId,
                    getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request),
                    ActionListener.wrap(future::complete, future::completeExceptionally)
                );

            // Wait for validation
            future.get(5, SECONDS);

        } catch (Exception e) {
            throw (RuntimeException) (e.getCause());
        }
    }

    private void loadModelAndExecuteStreaming(
        NodeClient client,
        String modelId,
        String userAlgorithm,
        RestRequest request,
        BytesReference chunkContent,
        StreamingRestChannel channel,
        CompletableFuture<HttpChunk> future
    ) {
        ActionListener<MLModel> listener = ActionListener.wrap(mlModel -> {
            try {
                String modelType = mlModel.getAlgorithm().name();
                String modelAlgorithm = Objects.requireNonNullElse(userAlgorithm, modelType);

                MLPredictionTaskRequest taskRequest = getRequest(modelId, modelAlgorithm, request, chunkContent);
                executeStreamingRequest(client, taskRequest, channel, future);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        }, future::completeExceptionally);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            modelManager
                .getModel(
                    modelId,
                    getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request),
                    ActionListener.runBefore(listener, context::restore)
                );
        }
    }

    private void executeStreamingRequest(
        NodeClient client,
        MLPredictionTaskRequest taskRequest,
        StreamingRestChannel channel,
        CompletableFuture<HttpChunk> future
    ) {
        StreamTransportResponseHandler<MLTaskResponse> handler = new StreamTransportResponseHandler<MLTaskResponse>() {
            @Override
            public void handleStreamResponse(StreamTransportResponse<MLTaskResponse> streamResponse) {
                try {
                    MLTaskResponse response = streamResponse.nextResponse();
                    if (response != null) {
                        HttpChunk responseChunk = convertToHttpChunk(response);
                        channel.sendChunk(responseChunk);

                        // Recursively handle the next response
                        client.threadPool().executor(STREAM_PREDICT_THREAD_POOL).execute(() -> handleStreamResponse(streamResponse));
                    } else {
                        log.info("No more responses, closing stream");
                        future.complete(XContentHttpChunk.last());
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    log.error("Error in stream handling", e);
                }
            }

            @Override
            public void handleException(TransportException exp) {
                future.completeExceptionally(exp);
            }

            @Override
            public String executor() {
                return ThreadPool.Names.SAME;
            }

            @Override
            public MLTaskResponse read(StreamInput in) throws IOException {
                return new MLTaskResponse(in);
            }
        };

        // Send the streaming request using transport service
        TransportPredictionStreamTaskAction.streamTransportService
            .sendRequest(
                clusterService.localNode(),
                MLPredictionStreamTaskAction.NAME,
                taskRequest,
                TransportRequestOptions.builder().withType(TransportRequestOptions.Type.STREAM).build(),
                handler
            );
    }

    /**
     * Creates a MLPredictionTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLPredictionTaskRequest
     */
    @VisibleForTesting
    MLPredictionTaskRequest getRequest(String modelId, String algorithm, RestRequest request, BytesReference content) throws IOException {
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        ActionType actionType = ActionType.from(getActionTypeFromRestRequest(request));
        if (FunctionName.REMOTE.name().equals(algorithm) && !mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
            throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
        } else if (FunctionName.isDLModel(FunctionName.from(algorithm.toUpperCase(Locale.ROOT)))
            && !mlFeatureEnabledSetting.isLocalModelEnabled()) {
            throw new IllegalStateException(LOCAL_MODEL_DISABLED_ERR_MSG);
        } else if (ActionType.BATCH_PREDICT == actionType && !mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()) {
            throw new IllegalStateException(BATCH_INFERENCE_DISABLED_ERR_MSG);
        } else if (!ActionType.isValidActionInModelPrediction(actionType)) {
            throw new IllegalArgumentException("Wrong action type in the rest request path!");
        }

        XContentParser parser = request
            .getMediaType()
            .xContent()
            .createParser(request.getXContentRegistry(), LoggingDeprecationHandler.INSTANCE, content.streamInput());

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInput mlInput = MLInput.parse(parser, algorithm, actionType);
        if (FunctionName.REMOTE.name().contentEquals(algorithm)) {
            RemoteInferenceMLInput input = (RemoteInferenceMLInput) mlInput;
            RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) input.getInputDataset();
            inputDataSet.getParameters().put("stream", String.valueOf(true));
            return new MLPredictionTaskRequest(modelId, input, null, tenantId);
        }
        return new MLPredictionTaskRequest(modelId, mlInput, null, tenantId);
    }

    private HttpChunk convertToHttpChunk(MLTaskResponse response) throws IOException {
        String sseData;
        boolean isLast = false;

        try {
            Map<String, ?> dataMap = extractDataMap(response);

            if (dataMap.containsKey("error")) {
                // Error response
                String errorMessage = (String) dataMap.get("error");
                sseData = String.format("data: {\"error\": \"%s\"}\n\n", errorMessage.replace("\"", "\\\"").replace("\n", "\\n"));
                isLast = true;
            } else {
                // Regular response - extract content and build proper structure
                String content = dataMap.containsKey("content") ? (String) dataMap.get("content") : "";
                isLast = dataMap.containsKey("is_last") ? Boolean.TRUE.equals(dataMap.get("is_last")) : false;

                // Create the proper response structure
                Map<String, Object> chunkData = new LinkedHashMap<>();
                chunkData.put("content", content);
                chunkData.put("is_last", isLast);

                ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(chunkData).build();
                ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
                ModelTensorOutput tensorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

                XContentBuilder builder = XContentFactory.jsonBuilder();
                tensorOutput.toXContent(builder, ToXContent.EMPTY_PARAMS);
                sseData = "data: " + builder.toString() + "\n\n";
            }
        } catch (Exception e) {
            log.error("Failed to process response", e);
            sseData = "data: {\"error\": \"Processing failed\"}\n\n";
            isLast = true;
        }
        return createHttpChunk(sseData, isLast);
    }

    private Map<String, ?> extractDataMap(MLTaskResponse response) {
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        if (output != null && !output.getMlModelOutputs().isEmpty()) {
            ModelTensors tensors = output.getMlModelOutputs().get(0);
            if (!tensors.getMlModelTensors().isEmpty()) {
                return tensors.getMlModelTensors().get(0).getDataAsMap();
            }
        }
        return Map.of();
    }

    private HttpChunk createHttpChunk(String sseData, boolean isLast) {
        BytesReference bytesRef = BytesReference.fromByteBuffer(ByteBuffer.wrap(sseData.getBytes()));
        return new HttpChunk() {
            @Override
            public void close() {
                if (bytesRef instanceof Releasable)
                    ((Releasable) bytesRef).close();
            }

            @Override
            public boolean isLast() {
                return isLast;
            }

            @Override
            public BytesReference content() {
                return bytesRef;
            }
        };
    }
}

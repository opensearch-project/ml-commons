/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.plugin.MachineLearningPlugin.STREAM_EXECUTE_THREAD_POOL;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.STREAM_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.isAsync;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.lease.Releasable;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.support.XContentHttpChunk;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.http.HttpChunk;
import org.opensearch.ml.action.execute.TransportExecuteStreamTaskAction;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteStreamTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
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

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
public class RestMLExecuteStreamAction extends BaseRestHandler {

    private static final String ML_EXECUTE_STREAM_ACTION = "ml_execute_stream_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private ClusterService clusterService;

    /**
     * Constructor
     */
    public RestMLExecuteStreamAction(MLFeatureEnabledSetting mlFeatureEnabledSetting, ClusterService clusterService) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.clusterService = clusterService;
    }

    @Override
    public String getName() {
        return ML_EXECUTE_STREAM_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/agents/{%s}/_execute/stream", ML_BASE_URI, PARAMETER_AGENT_ID)
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

        String agentId = request.param(PARAMETER_AGENT_ID);

        final StreamingRestChannelConsumer consumer = (channel) -> {
            Map<String, List<String>> headers = Map
                .of(
                    "Content-Type",
                    List.of("text/event-stream"),
                    "Cache-Control",
                    List.of("no-cache"),
                    "Connection",
                    List.of("keep-alive")
                );
            channel.prepareResponse(RestStatus.OK, headers);

            Flux.from(channel).ofType(HttpChunk.class).concatMap(chunk -> {
                final CompletableFuture<HttpChunk> future = new CompletableFuture<>();
                try {
                    MLExecuteTaskRequest mlExecuteTaskRequest = getRequest(agentId, request, chunk.content());
                    StreamTransportResponseHandler<MLTaskResponse> handler = new StreamTransportResponseHandler<MLTaskResponse>() {
                        @Override
                        public void handleStreamResponse(StreamTransportResponse<MLTaskResponse> streamResponse) {
                            try {
                                MLTaskResponse response = streamResponse.nextResponse();

                                if (response != null) {
                                    HttpChunk responseChunk = convertToHttpChunk(response);
                                    channel.sendChunk(responseChunk);

                                    // Recursively handle the next response
                                    client
                                        .threadPool()
                                        .executor(STREAM_EXECUTE_THREAD_POOL)
                                        .execute(() -> handleStreamResponse(streamResponse));
                                } else {
                                    log.info("No more responses, closing stream");
                                    future.complete(XContentHttpChunk.last());
                                    streamResponse.close();
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

                    TransportExecuteStreamTaskAction.streamTransportService
                        .sendRequest(
                            clusterService.localNode(),
                            MLExecuteStreamTaskAction.NAME,
                            mlExecuteTaskRequest,
                            TransportRequestOptions.builder().withType(TransportRequestOptions.Type.STREAM).build(),
                            handler
                        );

                } catch (IOException e) {
                    throw new MLException("Got an exception in flux.", e);
                }

                return Mono.fromCompletionStage(future);
            }).doOnNext(channel::sendChunk).onErrorComplete(ex -> {
                // Error handling
                try {
                    channel.sendResponse(new BytesRestResponse(channel, (Exception) ex));
                    return true;
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).subscribe();
        };

        return channel -> {
            if (channel instanceof StreamingRestChannel) {
                consumer.accept((StreamingRestChannel) channel);
            } else {
                final ActionRequestValidationException validationError = new ActionRequestValidationException();
                validationError.addValidationError("Unable to initiate request / response streaming over non-streaming channel");
                channel.sendResponse(new BytesRestResponse(channel, validationError));
            }
        };
    }

    /**
     * Creates a MLExecuteTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLExecuteTaskRequest
     */
    @VisibleForTesting
    MLExecuteTaskRequest getRequest(String agentId, RestRequest request, BytesReference content) throws IOException {
        XContentParser parser = request
            .getMediaType()
            .xContent()
            .createParser(request.getXContentRegistry(), LoggingDeprecationHandler.INSTANCE, content.streamInput());
        boolean async = isAsync(request);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
        }
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        FunctionName functionName = FunctionName.AGENT;
        Input input = MLInput.parse(parser, functionName.name());
        AgentMLInput agentInput = (AgentMLInput) input;
        agentInput.setAgentId(agentId);
        agentInput.setTenantId(tenantId);
        agentInput.setIsAsync(async);
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentInput.getInputDataset();
        inputDataSet.getParameters().put("stream", String.valueOf(true));
        return new MLExecuteTaskRequest(functionName, input);
    }

    // TODO: refactor this method
    private HttpChunk convertToHttpChunk(MLTaskResponse response) throws IOException {
        String memoryId = "";
        String parentInteractionId = "";
        String content = "";
        boolean isLast = false;

        // Extract values from multiple tensors
        try {
            ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
            if (output != null && !output.getMlModelOutputs().isEmpty()) {
                ModelTensors modelTensors = output.getMlModelOutputs().get(0);
                List<ModelTensor> tensors = modelTensors.getMlModelTensors();

                for (ModelTensor tensor : tensors) {
                    String name = tensor.getName();
                    if ("memory_id".equals(name) && tensor.getResult() != null) {
                        memoryId = tensor.getResult();
                    } else if ("parent_interaction_id".equals(name) && tensor.getResult() != null) {
                        parentInteractionId = tensor.getResult();
                    } else if (("llm_response".equals(name) || "response".equals(name)) && tensor.getDataAsMap() != null) {
                        Map<String, ?> dataMap = tensor.getDataAsMap();
                        if (dataMap.containsKey("content")) {
                            content = (String) dataMap.get("content");
                            if (content == null)
                                content = "";
                        }
                        if (dataMap.containsKey("is_last")) {
                            isLast = Boolean.TRUE.equals(dataMap.get("is_last"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract values from response", e);
        }

        String finalContent = content;
        boolean finalIsLast = isLast;

        log
            .info(
                "Converting to HttpChunk - memoryId: '{}', parentId: '{}', content: '{}', isLast: {}",
                memoryId,
                parentInteractionId,
                content,
                isLast
            );

        // Create ordered tensors
        List<ModelTensor> orderedTensors = List
            .of(
                ModelTensor.builder().name("memory_id").result(memoryId).build(),
                ModelTensor.builder().name("parent_interaction_id").result(parentInteractionId).build(),
                ModelTensor.builder().name("response").dataAsMap(new LinkedHashMap<String, Object>() {
                    {
                        put("content", finalContent);
                        put("is_last", finalIsLast);
                    }
                }).build()
            );

        ModelTensors tensors = ModelTensors.builder().mlModelTensors(orderedTensors).build();

        ModelTensorOutput tensorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        tensorOutput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonData = builder.toString();

        String sseData = "data: " + jsonData + "\n\n";
        return createHttpChunk(sseData, isLast);
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

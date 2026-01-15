/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AG_UI_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.plugin.MachineLearningPlugin.STREAM_EXECUTE_THREAD_POOL;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.STREAM_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.hasMcpHeaders;
import static org.opensearch.ml.utils.RestActionUtils.isAsync;
import static org.opensearch.ml.utils.RestActionUtils.putMcpRequestHeaders;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.get.GetRequest;
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
import org.opensearch.ml.action.execute.TransportExecuteStreamTaskAction;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agui.*;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
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
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.StreamTransportResponseHandler;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportRequestOptions;
import org.opensearch.transport.client.node.NodeClient;
import org.opensearch.transport.stream.StreamTransportResponse;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
public class RestMLExecuteStreamAction extends BaseRestHandler {

    private static final String ML_EXECUTE_STREAM_ACTION = "ml_execute_stream_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private ClusterService clusterService;
    private MLModelManager mlModelManager;

    /**
     * Constructor
     */
    public RestMLExecuteStreamAction(
        MLModelManager mlModelManager,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ClusterService clusterService
    ) {
        this.mlModelManager = mlModelManager;
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

        // Check MCP header passthrough feature flag
        if (hasMcpHeaders(request) && !mlFeatureEnabledSetting.isMcpHeaderPassthroughEnabled()) {
            throw new IllegalArgumentException(
                "MCP header passthrough is not enabled. To enable, please update the setting: "
                    + "plugins.ml_commons.mcp_header_passthrough_enabled"
            );
        }
        putMcpRequestHeaders(request, client);

        if (!mlFeatureEnabledSetting.isStreamEnabled()) {
            throw new IllegalStateException(STREAM_DISABLED_ERR_MSG);
        }

        String agentId = request.param(PARAMETER_AGENT_ID);

        // Validate agent and model synchronously before starting stream
        MLAgent agent = validateAndGetAgent(agentId, client);
        if (agent.getLlm() != null && agent.getLlm().getModelId() != null) {
            if (!isModelValid(agent.getLlm().getModelId(), request, client)) {
                throw new OpenSearchStatusException("Failed to find model", RestStatus.NOT_FOUND);
            }
        }

        final StreamingRestChannelConsumer consumer = (channel) -> {

            Supplier<ThreadContext.StoredContext> supplier = client.threadPool().getThreadContext().newRestorableContext(true);

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

            Flux.from(channel).ofType(HttpChunk.class).collectList().flatMap(chunks -> {
                try (ThreadContext.StoredContext context = supplier.get()) {

                    BytesReference completeContent = combineChunks(chunks);
                    MLExecuteTaskRequest mlExecuteTaskRequest = getRequest(agentId, request, completeContent, client);
                    boolean isAGUI = isAGUIAgent(mlExecuteTaskRequest);

                    // Send RUN_STARTED event immediately for AG-UI agents (ReAct cycle begins)
                    if (isAGUI) {
                        String threadId = extractThreadId(mlExecuteTaskRequest);
                        String runId = extractRunId(mlExecuteTaskRequest);

                        BaseEvent runStartedEvent = new RunStartedEvent(threadId, runId);
                        HttpChunk startChunk = createHttpChunk("data: " + runStartedEvent.toJsonString() + "\n\n", false);
                        channel.sendChunk(startChunk);
                        log.debug("AG-UI: RestMLExecuteStreamAction: Sent RUN_STARTED event - threadId={}, runId={}", threadId, runId);
                    }

                    final CompletableFuture<HttpChunk> future = new CompletableFuture<>();
                    StreamTransportResponseHandler<MLTaskResponse> handler = new StreamTransportResponseHandler<MLTaskResponse>() {
                        @Override
                        public void handleStreamResponse(StreamTransportResponse<MLTaskResponse> streamResponse) {
                            try {
                                MLTaskResponse response = streamResponse.nextResponse();

                                if (response != null) {
                                    HttpChunk responseChunk = convertToHttpChunk(response, isAGUI);
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

                    StreamTransportService streamTransportService = TransportExecuteStreamTaskAction.getStreamTransportService();
                    streamTransportService
                        .sendRequest(
                            clusterService.localNode(),
                            MLExecuteStreamTaskAction.NAME,
                            mlExecuteTaskRequest,
                            TransportRequestOptions.builder().withType(TransportRequestOptions.Type.STREAM).build(),
                            handler
                        );

                    return Mono.fromCompletionStage(future);
                } catch (Exception e) {
                    log.error("Failed to parse or process request", e);
                    return Mono.error(e);
                }
            }).doOnNext(channel::sendChunk).onErrorResume(ex -> {
                log.error("Error occurred", ex);
                try {
                    String errorMessage = ex instanceof IOException
                        ? "Failed to parse request: " + ex.getMessage()
                        : "Error processing request: " + ex.getMessage();
                    HttpChunk errorChunk = createHttpChunk("data: {\"error\": \"" + errorMessage.replace("\"", "\\\"") + "\"}\n\n", true);
                    channel.sendChunk(errorChunk);
                } catch (Exception e) {
                    log.error("Failed to send error chunk", e);
                }
                return Mono.empty();
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

    @VisibleForTesting
    MLAgent validateAndGetAgent(String agentId, NodeClient client) {
        try {
            CompletableFuture<MLAgent> future = new CompletableFuture<>();

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.get(new GetRequest(ML_AGENT_INDEX, agentId), ActionListener.runBefore(ActionListener.wrap(response -> {
                    if (response.isExists()) {
                        try {
                            XContentParser parser = jsonXContent
                                .createParser(null, LoggingDeprecationHandler.INSTANCE, response.getSourceAsString());
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            future.complete(MLAgent.parse(parser));
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    } else {
                        future.completeExceptionally(new OpenSearchStatusException("Agent not found", RestStatus.NOT_FOUND));
                    }
                }, future::completeExceptionally), context::restore));
            }

            // TODO: Make validation async
            return future.get(5, SECONDS);
        } catch (Exception e) {
            log.error("Failed to validate agent {}", agentId, e);
            throw new OpenSearchStatusException("Failed to find agent with the provided agent id: " + agentId, RestStatus.NOT_FOUND);
        }
    }

    @VisibleForTesting
    boolean isModelValid(String modelId, RestRequest request, NodeClient client) throws IOException {
        try {
            CompletableFuture<MLModel> future = new CompletableFuture<>();

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                mlModelManager
                    .getModel(
                        modelId,
                        getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request),
                        ActionListener.runBefore(ActionListener.wrap(future::complete, future::completeExceptionally), context::restore)
                    );
            }

            // TODO: make model validation async
            future.get(5, SECONDS);
            return true;
        } catch (Exception e) {
            log.error("Failed to validate model {}", e.getMessage());
            return false;
        }
    }

    /**
     * Creates a MLExecuteTaskRequest from a RestRequest
     *
     * @param agentId Agent ID
     * @param request RestRequest
     * @param content Request content
     * @param client NodeClient
     * @return MLExecuteTaskRequest
     */
    @VisibleForTesting
    MLExecuteTaskRequest getRequest(String agentId, RestRequest request, BytesReference content, NodeClient client) throws IOException {
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

        // Check if this is AG-UI input format
        String requestBodyJson = content.utf8ToString();
        Input input;
        if (AGUIInputConverter.isAGUIInput(requestBodyJson)) {
            if (!mlFeatureEnabledSetting.isAGUIEnabled()) {
                throw new IllegalStateException(ML_COMMONS_AG_UI_DISABLED_MESSAGE);
            }
            log.debug("AG-UI: Detected AG-UI input format for streaming agent: {}", agentId);
            input = AGUIInputConverter.convertFromAGUIInput(requestBodyJson, agentId, tenantId, async);
        } else {
            input = MLInput.parse(parser, functionName.name());
            AgentMLInput agentInput = (AgentMLInput) input;
            agentInput.setAgentId(agentId);
            agentInput.setTenantId(tenantId);
            agentInput.setIsAsync(async);
        }

        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) ((AgentMLInput) input).getInputDataset();
        inputDataSet.getParameters().put("stream", String.valueOf(true));
        return new MLExecuteTaskRequest(functionName, input);
    }

    private boolean isAGUIAgent(MLExecuteTaskRequest request) {
        if (request.getInput() instanceof AgentMLInput agentInput) {
            RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentInput.getInputDataset();

            // Check if this request came from AG-UI by looking for AG-UI specific parameters
            return inputDataSet.getParameters().containsKey(AGUI_PARAM_THREAD_ID)
                || inputDataSet.getParameters().containsKey(AGUI_PARAM_RUN_ID);
        }
        return false;
    }

    private String extractThreadId(MLExecuteTaskRequest request) {
        if (request.getInput() instanceof AgentMLInput) {
            AgentMLInput agentInput = (AgentMLInput) request.getInput();
            RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentInput.getInputDataset();
            String threadId = inputDataSet.getParameters().get(AGUI_PARAM_THREAD_ID);
            return threadId != null ? threadId : "thread_" + System.currentTimeMillis();
        }
        return "thread_" + System.currentTimeMillis();
    }

    private String extractRunId(MLExecuteTaskRequest request) {
        if (request.getInput() instanceof AgentMLInput) {
            AgentMLInput agentInput = (AgentMLInput) request.getInput();
            RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentInput.getInputDataset();
            String runId = inputDataSet.getParameters().get(AGUI_PARAM_RUN_ID);
            return runId != null ? runId : "run_" + System.currentTimeMillis();
        }
        return "run_" + System.currentTimeMillis();
    }

    private HttpChunk convertToHttpChunk(MLTaskResponse response, boolean isAGUIAgent) throws IOException {
        String memoryId = "";
        String parentInteractionId = "";
        String content = "";
        boolean isLast = false;

        try {
            Map<String, ?> dataMap = extractDataMap(response);

            if (dataMap.containsKey("error")) {
                // Error response - handle errors
                content = (String) dataMap.get("error");
                isLast = true;
            } else {
                // TODO: refactor to handle other types of agents
                // Regular response - extract values and build proper structure
                memoryId = extractTensorResult(response, "memory_id");
                parentInteractionId = extractTensorResult(response, "parent_interaction_id");
                content = dataMap.containsKey("content") ? (String) dataMap.get("content") : "";
                isLast = dataMap.containsKey("is_last") && Boolean.TRUE.equals(dataMap.get("is_last"));
            }
        } catch (Exception e) {
            log.error("Failed to process response", e);
            content = "Processing failed";
            isLast = true;
        }

        String finalContent = content;
        boolean finalIsLast = isLast;

        // If this is an AG-UI agent, convert to AG-UI event format
        if (isAGUIAgent) {
            return convertToAGUIEvent(content, isLast);
        }

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

    private String extractTensorResult(MLTaskResponse response, String tensorName) {
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        if (output != null && !output.getMlModelOutputs().isEmpty()) {
            ModelTensors tensors = output.getMlModelOutputs().get(0);
            for (ModelTensor tensor : tensors.getMlModelTensors()) {
                if (tensorName.equals(tensor.getName()) && tensor.getResult() != null) {
                    return tensor.getResult();
                }
            }
        }
        return "";
    }

    private Map<String, ?> extractDataMap(MLTaskResponse response) {
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        if (output != null && !output.getMlModelOutputs().isEmpty()) {
            ModelTensors tensors = output.getMlModelOutputs().get(0);
            for (ModelTensor tensor : tensors.getMlModelTensors()) {
                String name = tensor.getName();
                if ("error".equals(name) || "llm_response".equals(name) || "response".equals(name)) {
                    Map<String, ?> dataMap = tensor.getDataAsMap();
                    if (dataMap != null) {
                        return dataMap;
                    }
                }
            }
        }
        return Map.of();
    }

    private HttpChunk convertToAGUIEvent(String content, boolean isLast) {
        log
            .debug(
                "RestMLExecuteStreamAction: convertToAGUIEvent() called - contentLength={}, isLast={}",
                content != null ? content.length() : "null",
                isLast
            );

        StringBuilder sseResponse = new StringBuilder();

        if (content != null && !content.isEmpty()) {
            log.debug("RestMLExecuteStreamAction: Processing content: '{}'", content);

            try {
                if (StringUtils.isJson(content)) {
                    JsonElement element = JsonParser.parseString(content);
                    sseResponse.append("data: ").append(element).append("\n\n");
                    log.debug("RestMLExecuteStreamAction: Processing json element: '{}'", element);
                } else {
                    // catch unexpected content chunks such as Bedrock error
                    log.warn("Unexpected content received - not valid JSON: {}", content);
                    BaseEvent runErrorEvent = new RunErrorEvent("Unexpected chunk: " + content, null);
                    sseResponse.append("data: ").append(runErrorEvent.toJsonString()).append("\n\n");
                    isLast = true;
                }
            } catch (Exception e) {
                log.error("Failed to process AG-UI events chunk content {}", content, e);
                BaseEvent runErrorEvent = new RunErrorEvent("Unexpected error: " + e.getMessage(), null);
                sseResponse.append("data: ").append(runErrorEvent.toJsonString()).append("\n\n");
                isLast = true;
            }
        } else {
            log.warn("Received null or empty AG-UI content chunk");
        }

        String finalSse = sseResponse.toString();
        log.debug("RestMLExecuteStreamAction: Returning chunk - length={}", finalSse.length());
        return createHttpChunk(finalSse, isLast);
    }

    @VisibleForTesting
    BytesReference combineChunks(List<HttpChunk> chunks) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (HttpChunk chunk : chunks) {
                chunk.content().writeTo(buffer);
            }
            return BytesReference.fromByteBuffer(ByteBuffer.wrap(buffer.toByteArray()));
        } catch (IOException e) {
            log.error("Failed to combine chunks", e);
            throw new OpenSearchStatusException("Failed to combine request chunks", RestStatus.INTERNAL_SERVER_ERROR, e);
        }
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

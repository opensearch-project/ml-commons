/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.plugin.MachineLearningPlugin.STREAM_EXECUTE_THREAD_POOL;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.STREAM_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.isAsync;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.function_calling.OpenaiV1ChatCompletionsFunctionCalling;
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
            Map<String, List<String>> headers = Map
                .of(
                    "Content-Type",
                    List.of("text/event-stream"),
                    "Cache-Control",
                    List.of("no-cache"),
                    "Connection",
                    List.of("keep-alive"),
                    "Access-Control-Allow-Origin",
                    List.of("*"),
                    "Access-Control-Allow-Methods",
                    List.of("GET, POST, PUT, DELETE, OPTIONS, HEAD"),
                    "Access-Control-Allow-Headers",
                    List.of("X-Requested-With,X-Auth-Token,Content-Type,Content-Length,Authorization")
                );
            channel.prepareResponse(RestStatus.OK, headers);

            Flux.from(channel).ofType(HttpChunk.class).collectList().flatMap(chunks -> {
                try {
                    BytesReference completeContent = combineChunks(chunks);
                    MLExecuteTaskRequest mlExecuteTaskRequest = getRequest(agentId, request, completeContent);
                    boolean isAGUI = isAGUIAgent(mlExecuteTaskRequest);

                    // Extract backend tool names from agent configuration and add to request for AG-UI filtering
                    List<String> backendToolNames = extractBackendToolNamesFromAgent(agent);
                    if (isAGUI && !backendToolNames.isEmpty()) {
                        // Add backend tool names to request parameters so they're available during streaming
                        RemoteInferenceInputDataSet inputDataSet =
                            (RemoteInferenceInputDataSet) ((org.opensearch.ml.common.input.execute.agent.AgentMLInput) mlExecuteTaskRequest
                                .getInput()).getInputDataset();
                        inputDataSet.getParameters().put("backend_tool_names", new com.google.gson.Gson().toJson(backendToolNames));
                        log
                            .info(
                                "AG-UI: Added {} backend tool names to request for streaming filter: {}",
                                backendToolNames.size(),
                                backendToolNames
                            );
                    }

                    // Extract backend tool names from request parameters for AG-UI filtering
                    List<String> backendToolNamesFromRequest = extractBackendToolNames(mlExecuteTaskRequest);

                    final CompletableFuture<HttpChunk> future = new CompletableFuture<>();
                    StreamTransportResponseHandler<MLTaskResponse> handler = new StreamTransportResponseHandler<MLTaskResponse>() {
                        @Override
                        public void handleStreamResponse(StreamTransportResponse<MLTaskResponse> streamResponse) {
                            try {
                                MLTaskResponse response = streamResponse.nextResponse();

                                if (response != null) {
                                    HttpChunk responseChunk = convertToHttpChunk(response, isAGUI, backendToolNamesFromRequest);
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

        // Check if this is AG-UI input format
        String requestBodyJson = content.utf8ToString();
        Input input;
        if (org.opensearch.ml.common.agui.AGUIInputConverter.isAGUIInput(requestBodyJson)) {
            log.info("Detected AG-UI input format for streaming agent: {}", agentId);
            input = org.opensearch.ml.common.agui.AGUIInputConverter.convertFromAGUIInput(requestBodyJson, agentId, tenantId, async);
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
        if (request.getInput() instanceof org.opensearch.ml.common.input.execute.agent.AgentMLInput) {
            org.opensearch.ml.common.input.execute.agent.AgentMLInput agentInput =
                (org.opensearch.ml.common.input.execute.agent.AgentMLInput) request.getInput();
            org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet inputDataSet =
                (org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet) agentInput.getInputDataset();

            // Check if this request came from AG-UI by looking for AG-UI specific parameters
            return inputDataSet.getParameters().containsKey("agui_thread_id") || inputDataSet.getParameters().containsKey("agui_run_id");
        }
        return false;
    }

    private List<String> extractBackendToolNamesFromAgent(MLAgent agent) {
        List<String> backendToolNames = new ArrayList<>();
        if (agent != null && agent.getTools() != null) {
            for (org.opensearch.ml.common.agent.MLToolSpec toolSpec : agent.getTools()) {
                if (toolSpec.getName() != null) {
                    backendToolNames.add(toolSpec.getName());
                }
            }
        }
        return backendToolNames;
    }

    private List<String> extractBackendToolNames(MLExecuteTaskRequest request) {
        if (request.getInput() instanceof org.opensearch.ml.common.input.execute.agent.AgentMLInput) {
            org.opensearch.ml.common.input.execute.agent.AgentMLInput agentInput =
                (org.opensearch.ml.common.input.execute.agent.AgentMLInput) request.getInput();
            org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet inputDataSet =
                (org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet) agentInput.getInputDataset();

            String backendToolNamesJson = inputDataSet.getParameters().get("backend_tool_names");
            if (backendToolNamesJson != null && !backendToolNamesJson.isEmpty()) {
                try {
                    com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(backendToolNamesJson);
                    if (element.isJsonArray()) {
                        List<String> toolNames = new ArrayList<>();
                        for (com.google.gson.JsonElement toolElement : element.getAsJsonArray()) {
                            toolNames.add(toolElement.getAsString());
                        }
                        log.info("AG-UI: Extracted {} backend tool names for filtering: {}", toolNames.size(), toolNames);
                        return toolNames;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse backend_tool_names from request: {}", e.getMessage());
                }
            }
        }
        return List.of();
    }

    private HttpChunk convertToHttpChunk(MLTaskResponse response, boolean isAGUIAgent, List<String> backendToolNames) throws IOException {
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
                isLast = dataMap.containsKey("is_last") ? Boolean.TRUE.equals(dataMap.get("is_last")) : false;
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
            // Check if this is a raw OpenAI function call chunk that should be filtered out
            if (isRawOpenAIFunctionCallChunk(content)) {
                log.debug("AG-UI: Filtering out raw OpenAI function call chunk: {}", content);
                // Return an empty chunk that won't be sent to client
                return createHttpChunk("", false);
            }
            return convertToAGUIEvent(content, memoryId, parentInteractionId, isLast, backendToolNames);
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

    private HttpChunk convertToAGUIEvent(
        String content,
        String memoryId,
        String parentInteractionId,
        boolean isLast,
        List<String> backendToolNames
    ) throws IOException {
        StringBuilder sseResponse = new StringBuilder();

        // Check if content already contains AG-UI events (JSON array format)
        if (content != null && !content.isEmpty()) {
            try {
                // Try to parse as AG-UI events JSON array
                com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(content);
                if (element.isJsonArray()) {
                    // Content is already AG-UI events - stream them directly
                    com.google.gson.JsonArray events = element.getAsJsonArray();
                    for (com.google.gson.JsonElement eventElement : events) {
                        if (eventElement.isJsonObject()) {
                            com.google.gson.JsonObject event = eventElement.getAsJsonObject();
                            String eventType = event.has("type") ? event.get("type").getAsString() : "unknown";

                            // Add proper SSE formatting for each event
                            sseResponse.append("data: ").append(eventElement.toString()).append("\n\n");
                            log.debug("AG-UI: Streaming event type: {}", eventType);
                        }
                    }
                    return createHttpChunk(sseResponse.toString(), isLast);
                }
            } catch (Exception e) {
                log.debug("Content is not AG-UI events JSON, treating as regular content: {}", e.getMessage());
            }

            // Try to parse as OpenAI function call format using existing FunctionCalling logic
            if (tryParseAsOpenAIFunctionCall(content, sseResponse, memoryId, parentInteractionId, isLast, backendToolNames)) {
                // When we successfully parse function calls and generate RUN_FINISHED, this should be the last chunk
                return createHttpChunk(sseResponse.toString(), true);
            }
        }

        // Fallback: Create basic AG-UI events for regular content
        String threadId = memoryId != null ? memoryId : "thread_" + System.currentTimeMillis();
        String runId = parentInteractionId != null ? parentInteractionId : "run_" + System.currentTimeMillis();

        // Get required startup events (RUN_STARTED, TEXT_MESSAGE_START if needed)
        String[] startupEvents = AGUIStreamingEventManager.getRequiredStartEvents(threadId, runId);
        for (String event : startupEvents) {
            sseResponse.append("data: ").append(event).append("\n\n");
        }

        // Add content event if there's content
        if (content != null && !content.isEmpty()) {
            String contentEvent = AGUIStreamingEventManager.createTextMessageContentEvent(threadId, runId, content);
            sseResponse.append("data: ").append(contentEvent).append("\n\n");
        }

        // Add ending events if this is the last chunk
        if (isLast) {
            String[] endEvents = AGUIStreamingEventManager.getRequiredEndEvents(threadId, runId);
            for (String event : endEvents) {
                sseResponse.append("data: ").append(event).append("\n\n");
            }
        }

        return createHttpChunk(sseResponse.toString(), isLast);
    }

    private BytesReference combineChunks(List<HttpChunk> chunks) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (HttpChunk chunk : chunks) {
                chunk.content().writeTo(buffer);
            }
            return BytesReference.fromByteBuffer(ByteBuffer.wrap(buffer.toByteArray()));
        } catch (IOException e) {
            log.error("Failed to combine chunks", e);
            throw new RuntimeException("Failed to combine request chunks", e);
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

    /**
     * Try to parse content as OpenAI function call format and convert to AG-UI tool events.
     * Uses the existing FunctionCalling infrastructure to parse OpenAI responses.
     * Filters out backend tools from being converted to AG-UI events.
     *
     * @param content Raw content from LLM response
     * @param sseResponse StringBuilder to append AG-UI events
     * @param memoryId Memory ID for thread identification
     * @param parentInteractionId Parent interaction ID for run identification
     * @param isLast Whether this is the last chunk in the stream
     * @param backendToolNames List of backend tool names to filter out
     * @return true if content was successfully parsed as OpenAI function call, false otherwise
     */
    private boolean tryParseAsOpenAIFunctionCall(
        String content,
        StringBuilder sseResponse,
        String memoryId,
        String parentInteractionId,
        boolean isLast,
        List<String> backendToolNames
    ) {
        try {
            // Check if content looks like OpenAI function call format (streaming chunks OR complete response)
            // OpenAI streaming chunks: {"index":0.0,"id":"call_xxx","type":"function",...}
            // OpenAI complete response: {"choices":[{"message":{"tool_calls":[...]}}]}
            boolean isStreamingChunk = content.contains("\"index\":")
                && (content.contains("\"type\":\"function\"") || content.contains("\"function\":"));
            boolean isCompleteResponse = content.contains("\"choices\":") && content.contains("\"tool_calls\":");

            if (!isStreamingChunk && !isCompleteResponse) {
                return false;
            }

            log.debug("AG-UI: Attempting to parse as OpenAI function call: {}", content);

            // Create a ModelTensorOutput that wraps the raw OpenAI content
            // This simulates the structure that FunctionCalling.handle() expects
            Map<String, Object> llmResponseData = new LinkedHashMap<>();

            // Try to parse the content as JSON
            com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(content);
            if (element.isJsonObject()) {
                com.google.gson.JsonObject responseObj = element.getAsJsonObject();

                if (isCompleteResponse) {
                    // Content is already in the complete OpenAI response format
                    // {"choices":[{"message":{"tool_calls":[...]}}]}
                    // We can use it directly with FunctionCalling
                    llmResponseData = jsonObjectToMap(responseObj);
                } else if (isStreamingChunk) {
                    // Handle individual streaming chunk format
                    // {"index":0.0,"id":"call_xxx","type":"function",...}
                    if (responseObj.has("type")
                        && "function".equals(responseObj.get("type").getAsString())
                        && responseObj.has("id")
                        && responseObj.has("function")) {

                        // Convert streaming chunk to complete response format
                        Map<String, Object> functionCallMap = jsonObjectToMap(responseObj);
                        Map<String, Object> choice = new LinkedHashMap<>();
                        Map<String, Object> message = new LinkedHashMap<>();
                        message.put("tool_calls", List.of(functionCallMap));
                        choice.put("message", message);
                        choice.put("finish_reason", "tool_calls");
                        llmResponseData.put("choices", List.of(choice));
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }

                // Create ModelTensorOutput with the wrapped data
                ModelTensor responseTensor = ModelTensor.builder().name("response").dataAsMap(llmResponseData).build();

                ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(List.of(responseTensor)).build();

                ModelTensorOutput tensorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of(modelTensors)).build();

                // Use existing OpenAI function calling logic to parse
                FunctionCalling functionCalling = new OpenaiV1ChatCompletionsFunctionCalling();
                Map<String, String> params = new LinkedHashMap<>();
                functionCalling.configure(params);

                List<Map<String, String>> toolCalls = functionCalling.handle(tensorOutput, params);

                if (!toolCalls.isEmpty()) {
                    log.debug("AG-UI: Successfully parsed {} tool calls", toolCalls.size());

                    // Generate AG-UI events for the tool calls
                    String threadId = memoryId != null ? memoryId : "thread_" + System.currentTimeMillis();
                    String runId = parentInteractionId != null ? parentInteractionId : "run_" + System.currentTimeMillis();

                    // Get required startup events
                    String[] startupEvents = AGUIStreamingEventManager.getRequiredStartEvents(threadId, runId);
                    for (String event : startupEvents) {
                        sseResponse.append("data: ").append(event).append("\n\n");
                    }

                    // Generate tool call events for each tool call, filtering out backend tools
                    int frontendToolCallCount = 0;
                    for (Map<String, String> toolCall : toolCalls) {
                        String toolName = toolCall.get("tool_name");
                        String toolInput = toolCall.get("tool_input");
                        String toolCallId = toolCall.get("tool_call_id");

                        // Skip backend tools - they will be executed in the ReAct loop
                        if (backendToolNames != null && backendToolNames.contains(toolName)) {
                            log.info("AG-UI: Skipping backend tool '{}' from AG-UI events - will be executed in ReAct loop", toolName);
                            continue;
                        }

                        frontendToolCallCount++;

                        // Generate TOOL_CALL_START event
                        String toolCallStartEvent = String
                            .format(
                                "{\"type\":\"TOOL_CALL_START\",\"toolCallId\":\"%s\",\"toolCallName\":\"%s\",\"timestamp\":%d}",
                                toolCallId,
                                toolName,
                                System.currentTimeMillis()
                            );
                        sseResponse.append("data: ").append(toolCallStartEvent).append("\n\n");
                        log.debug("AG-UI: Generated TOOL_CALL_START event for frontend tool: {}", toolName);

                        // Generate TOOL_CALL_ARGS event
                        if (toolInput != null && !toolInput.isEmpty()) {
                            String toolCallArgsEvent = String
                                .format(
                                    "{\"type\":\"TOOL_CALL_ARGS\",\"toolCallId\":\"%s\",\"delta\":\"%s\",\"timestamp\":%d}",
                                    toolCallId,
                                    escapeJsonString(toolInput),
                                    System.currentTimeMillis()
                                );
                            sseResponse.append("data: ").append(toolCallArgsEvent).append("\n\n");
                            log.debug("AG-UI: Generated TOOL_CALL_ARGS event for frontend tool: {}", toolName);
                        }

                        // Generate TOOL_CALL_END event
                        String toolCallEndEvent = String
                            .format(
                                "{\"type\":\"TOOL_CALL_END\",\"toolCallId\":\"%s\",\"timestamp\":%d}",
                                toolCallId,
                                System.currentTimeMillis()
                            );
                        sseResponse.append("data: ").append(toolCallEndEvent).append("\n\n");
                        log.debug("AG-UI: Generated TOOL_CALL_END event for frontend tool: {}", toolName);
                    }

                    // Only return true if we generated events for frontend tools
                    if (frontendToolCallCount == 0) {
                        log.info("AG-UI: All tool calls were backend tools - not generating AG-UI events");
                        return false;
                    }

                    log
                        .info(
                            "AG-UI: Successfully converted OpenAI function call to {} AG-UI tool events (filtered {} backend tools)",
                            frontendToolCallCount,
                            toolCalls.size() - frontendToolCallCount
                        );

                    // For function calls, always add ending events since the frontend takes over tool execution
                    // The conversation should end here to allow the frontend to execute the tools
                    String[] endEvents = AGUIStreamingEventManager.getRequiredEndEvents(threadId, runId);
                    for (String event : endEvents) {
                        sseResponse.append("data: ").append(event).append("\n\n");
                    }

                    log.debug("AG-UI: Added ending events for function call - stream should close");
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.debug("AG-UI: Failed to parse content as OpenAI function call: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Convert JsonObject to Map<String, Object> recursively
     */
    private Map<String, Object> jsonObjectToMap(com.google.gson.JsonObject jsonObject) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : jsonObject.entrySet()) {
            map.put(entry.getKey(), jsonElementToObject(entry.getValue()));
        }
        return map;
    }

    /**
     * Convert JsonElement to Java Object recursively
     */
    private Object jsonElementToObject(com.google.gson.JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        } else if (element.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString();
            } else if (primitive.isNumber()) {
                return primitive.getAsNumber();
            } else if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
        } else if (element.isJsonObject()) {
            return jsonObjectToMap(element.getAsJsonObject());
        } else if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (com.google.gson.JsonElement arrayElement : element.getAsJsonArray()) {
                list.add(jsonElementToObject(arrayElement));
            }
            return list;
        }
        return null;
    }

    /**
     * Check if content is a raw OpenAI function call chunk that should be filtered out
     */
    private boolean isRawOpenAIFunctionCallChunk(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        try {
            // Check if content looks like OpenAI function call format (streaming chunks)
            // OpenAI streaming chunks: {"index":0.0,"id":"call_xxx","type":"function",...}
            boolean isStreamingChunk = content.contains("\"index\":")
                && (content.contains("\"type\":\"function\"") || content.contains("\"function\":"));

            if (isStreamingChunk) {
                // Parse as JSON to verify it's a valid OpenAI function call chunk
                com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(content);
                if (element.isJsonObject()) {
                    com.google.gson.JsonObject obj = element.getAsJsonObject();
                    return obj.has("index") && (obj.has("type") || obj.has("function"));
                }
            }
        } catch (Exception e) {
            // If parsing fails, it's not a valid JSON chunk
            log.debug("AG-UI: Content is not valid JSON, not filtering: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Escape special characters in JSON strings
     */
    private String escapeJsonString(String input) {
        if (input == null)
            return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}

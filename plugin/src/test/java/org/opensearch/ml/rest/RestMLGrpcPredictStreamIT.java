/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.protobufs.MLPredictModelStreamRequestBody;
import org.opensearch.protobufs.Messages;
import org.opensearch.protobufs.MlPredictModelStreamRequest;
import org.opensearch.protobufs.Parameters;
import org.opensearch.protobufs.PredictResponse;
import org.opensearch.protobufs.services.MLServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * End-to-end integration tests for the ML gRPC {@code PredictModelStream} API.
 */
public class RestMLGrpcPredictStreamIT extends MLCommonsRestTestCase {

    private static final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private static final String OPENAI_HOST = "api.openai.com";
    private static final String LLM_INTERFACE_OPENAI = "openai/v1/chat/completions";

    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
    private static final String BEDROCK_REGION = "us-west-2";
    private static final String BEDROCK_MODEL = "global.anthropic.claude-sonnet-4-5-20250929-v1:0";
    private static final String LLM_INTERFACE_BEDROCK = "bedrock/converse/claude";

    private static final int GRPC_PORT_RANGE_START = 9400;
    private static final int GRPC_PORT_RANGE_END = 9500;

    private final String grpcHost = System.getProperty("tests.grpc.host", "127.0.0.1");

    private ManagedChannel channel;

    @Before
    public void setupGrpcChannel() {
        // The gRPC aux transport is only enabled when the cluster is started with -Dgrpc=true. When it is not
        // enabled nothing in the range is listening, so skip rather than fail.
        int grpcPort = resolveGrpcPort();
        Assume
            .assumeTrue(
                "no reachable gRPC endpoint on "
                    + grpcHost
                    + " in port range ["
                    + GRPC_PORT_RANGE_START
                    + "-"
                    + GRPC_PORT_RANGE_END
                    + "] (start the cluster with -Dgrpc=true); skipping",
                grpcPort > 0
            );

        // Plaintext channel: the test cluster enables the non-secure transport-grpc aux transport
        channel = NettyChannelBuilder.forAddress(grpcHost, grpcPort).usePlaintext().build();
    }

    @After
    public void tearDownGrpcChannel() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    /**
     * Resolves the gRPC port to connect to. Honors an explicit {@code -Dtests.grpc.port} override; otherwise
     * probes the aux-transport port range and returns the first reachable port, or -1 if none is listening.
     */
    private int resolveGrpcPort() {
        String override = System.getProperty("tests.grpc.port");
        if (override != null) {
            int port = Integer.parseInt(override);
            return isGrpcPortReachable(port) ? port : -1;
        }
        for (int port = GRPC_PORT_RANGE_START; port <= GRPC_PORT_RANGE_END; port++) {
            if (isGrpcPortReachable(port)) {
                return port;
            }
        }
        return -1;
    }

    /** Returns true if a TCP connection to the given gRPC port on the configured host succeeds quickly. */
    private boolean isGrpcPortReachable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(grpcHost, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Skips a test unless an OpenAI key is available and the OpenAI endpoint is reachable. */
    private void assumeOpenAiAvailable() {
        Assume.assumeTrue("OPENAI_KEY is not set; skipping OpenAI gRPC streaming test", OPENAI_KEY != null);
        Assume.assumeTrue("api.openai.com is not reachable; skipping OpenAI gRPC streaming test", isServiceReachable(OPENAI_HOST));
    }

    /** Skips a test unless AWS credentials are available. */
    private void assumeBedrockAvailable() {
        Assume
            .assumeTrue(
                "AWS credentials are not set; skipping Bedrock gRPC streaming test",
                AWS_ACCESS_KEY_ID != null && AWS_SECRET_ACCESS_KEY != null && AWS_SESSION_TOKEN != null
            );
    }

    @Test
    public void testPredictModelStream_openAiStreamsChunksEndToEnd() throws Exception {
        assumeOpenAiAvailable();
        String modelId = registerAndDeployOpenAiModel(OPENAI_KEY);

        MlPredictModelStreamRequest request = predictRequest(modelId, chatMessageParameters());

        StreamCollector collector = new StreamCollector();
        MLServiceGrpc.newStub(channel).predictModelStream(request, collector);

        assertTrue("stream did not terminate within timeout", collector.awaitCompletion(60, TimeUnit.SECONDS));
        assertNull("stream terminated with error: " + collector.error(), collector.error());
        assertTrue("expected at least one streamed chunk", collector.responses().size() >= 1);
        assertTrue("stream should have completed", collector.completed());
    }

    @Test
    public void testPredictModelStream_bedrockStreamsChunksEndToEnd() throws Exception {
        assumeBedrockAvailable();
        String modelId = registerAndDeployBedrockModel(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN);

        MlPredictModelStreamRequest request = predictRequest(modelId, bedrockInputParameters());

        StreamCollector collector = new StreamCollector();
        MLServiceGrpc.newStub(channel).predictModelStream(request, collector);

        assertTrue("stream did not terminate within timeout", collector.awaitCompletion(60, TimeUnit.SECONDS));
        assertNull("stream terminated with error: " + collector.error(), collector.error());
        assertTrue("expected at least one streamed chunk", collector.responses().size() >= 1);
        assertTrue("stream should have completed", collector.completed());
    }

    @Test
    public void testPredictModelStream_invalidModelIdReturnsNotFound() throws Exception {
        assumeOpenAiAvailable();
        registerAndDeployOpenAiModel(OPENAI_KEY);

        MlPredictModelStreamRequest request = predictRequest("non-existent-model-id", chatMessageParameters());
        assertStreamError(request, Status.Code.NOT_FOUND);
    }

    @Test
    public void testPredictModelStream_missingRequestBodyReturnsInvalidArgument() throws Exception {
        assumeOpenAiAvailable();
        String modelId = registerAndDeployOpenAiModel(OPENAI_KEY);

        // No request body set at all -> ProtoRequestConverter rejects with IllegalArgumentException.
        MlPredictModelStreamRequest request = MlPredictModelStreamRequest.newBuilder().setModelId(modelId).build();
        assertStreamError(request, Status.Code.INVALID_ARGUMENT);
    }

    @Test
    public void testPredictModelStream_openAiInvalidPayloadReturnsInvalidArgument() throws Exception {
        assumeOpenAiAvailable();
        String modelId = registerAndDeployOpenAiModel(OPENAI_KEY);

        // The connector's request_body references ${parameters.messages}; omitting messages leaves the
        // placeholder unfilled, so the connector validation fails before the remote call.
        Parameters params = Parameters.newBuilder().setXLlmInterface(LLM_INTERFACE_OPENAI).build();
        MlPredictModelStreamRequest request = predictRequest(modelId, params);
        assertStreamError(request, Status.Code.INVALID_ARGUMENT);
    }

    @Test
    public void testPredictModelStream_openAiInvalidApiKeyReturnsUnauthenticated() throws Exception {
        assumeOpenAiAvailable();
        String modelId = registerAndDeployOpenAiModel("invalid_api_key");

        MlPredictModelStreamRequest request = predictRequest(modelId, chatMessageParameters());
        assertStreamError(request, Status.Code.UNAUTHENTICATED);
    }

    @Test
    public void testPredictModelStream_bedrockInvalidPayloadReturnsInvalidArgument() throws Exception {
        assumeBedrockAvailable();
        String modelId = registerAndDeployBedrockModel(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN);

        // The Bedrock connector's request_body references ${parameters.inputs}; omitting it leaves the
        // placeholder unfilled, so connector validation fails before the remote call.
        Parameters params = Parameters.newBuilder().setXLlmInterface(LLM_INTERFACE_BEDROCK).build();
        MlPredictModelStreamRequest request = predictRequest(modelId, params);
        assertStreamError(request, Status.Code.INVALID_ARGUMENT);
    }

    @Test
    public void testPredictModelStream_bedrockInvalidTokenReturnsPermissionDenied() throws Exception {
        assumeBedrockAvailable();
        String modelId = registerAndDeployBedrockModel("invalid_key", "invalid_secret", "invalid_token");

        MlPredictModelStreamRequest request = predictRequest(modelId, bedrockInputParameters());
        assertStreamError(request, Status.Code.PERMISSION_DENIED);
    }

    /** Standard chat-completion parameters: an OpenAI llm interface plus a single user message. */
    private Parameters chatMessageParameters() {
        return Parameters
            .newBuilder()
            // x_llm_interface is converted to _llm_interface by ProtoRequestConverter.
            .setXLlmInterface(LLM_INTERFACE_OPENAI)
            .addMessages(Messages.newBuilder().setRole("user").setContent("Say this is a test").build())
            .build();
    }

    /** Bedrock converse parameters: the bedrock llm interface plus a plain {@code inputs} prompt. */
    private Parameters bedrockInputParameters() {
        return Parameters.newBuilder().setXLlmInterface(LLM_INTERFACE_BEDROCK).setInputs("Say this is a test").build();
    }

    private MlPredictModelStreamRequest predictRequest(String modelId, Parameters parameters) {
        return MlPredictModelStreamRequest
            .newBuilder()
            .setModelId(modelId)
            .setMlPredictModelStreamRequestBody(MLPredictModelStreamRequestBody.newBuilder().setParameters(parameters).build())
            .build();
    }

    /** Runs a streaming call expected to fail, asserting it terminates via onError with the given gRPC status. */
    private void assertStreamError(MlPredictModelStreamRequest request, Status.Code expectedCode) throws InterruptedException {
        StreamCollector collector = new StreamCollector();
        MLServiceGrpc.newStub(channel).predictModelStream(request, collector);

        assertTrue("stream did not terminate within timeout", collector.awaitCompletion(60, TimeUnit.SECONDS));
        Throwable error = collector.error();
        assertNotNull("expected an error but stream completed successfully", error);
        assertFalse("stream should not complete successfully on error", collector.completed());
        assertTrue("error should be a StatusRuntimeException but was: " + error, error instanceof StatusRuntimeException);
        Status.Code actual = ((StatusRuntimeException) error).getStatus().getCode();
        assertEquals("unexpected gRPC status code (error: " + error.getMessage() + ")", expectedCode, actual);
    }

    /**
     * Registers and deploys a remote OpenAI chat-completions model configured for streaming.
     *
     * @param openAiKey the credential to embed in the connector (use an invalid value to test auth errors)
     * @return the deployed model id
     */
    private String registerAndDeployOpenAiModel(String openAiKey) throws Exception {
        String connectorEntity = "{\n"
            + "  \"name\": \"OpenAI streaming chat connector\",\n"
            + "  \"description\": \"OpenAI chat completions connector for gRPC streaming IT\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"http\",\n"
            + "  \"parameters\": {\n"
            + "      \"endpoint\": \"api.openai.com\",\n"
            + "      \"auth\": \"API_Key\",\n"
            + "      \"content_type\": \"application/json\",\n"
            + "      \"model\": \"gpt-3.5-turbo\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "      \"openAI_key\": \""
            + openAiKey
            + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "      {\n"
            + "          \"action_type\": \"predict\",\n"
            + "          \"method\": \"POST\",\n"
            + "          \"url\": \"https://api.openai.com/v1/chat/completions\",\n"
            + "          \"headers\": {\n"
            + "              \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "          },\n"
            + "          \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages}, \\\"stream\\\": ${parameters.stream} }\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";

        return registerAndDeploy(connectorEntity, "openAI-streaming-chat");
    }

    /**
     * Registers and deploys a remote Bedrock converse model configured for streaming, using AWS SigV4.
     *
     * @param accessKey    AWS access key
     * @param secretKey    AWS secret key
     * @param sessionToken AWS session token
     * @return the deployed model id
     */
    private String registerAndDeployBedrockModel(String accessKey, String secretKey, String sessionToken) throws Exception {
        String connectorEntity = "{\n"
            + "  \"name\": \"Amazon Bedrock Converse streaming connector\",\n"
            + "  \"description\": \"Bedrock converse connector for gRPC streaming IT\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"aws_sigv4\",\n"
            + "  \"credential\": {\n"
            + "      \"access_key\": \""
            + accessKey
            + "\",\n"
            + "      \"secret_key\": \""
            + secretKey
            + "\",\n"
            + "      \"session_token\": \""
            + sessionToken
            + "\"\n"
            + "  },\n"
            + "  \"parameters\": {\n"
            + "      \"region\": \""
            + BEDROCK_REGION
            + "\",\n"
            + "      \"service_name\": \"bedrock\",\n"
            + "      \"response_filter\": \"$.output.message.content[0].text\",\n"
            + "      \"model\": \""
            + BEDROCK_MODEL
            + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "      {\n"
            + "          \"action_type\": \"predict\",\n"
            + "          \"method\": \"POST\",\n"
            + "          \"headers\": {\n"
            + "              \"content-type\": \"application/json\"\n"
            + "          },\n"
            + "          \"url\": \"https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse\",\n"
            + "          \"request_body\": \"{\\\"messages\\\":[{\\\"role\\\":\\\"user\\\",\\\"content\\\":[{\\\"type\\\":\\\"text\\\",\\\"text\\\":\\\"${parameters.inputs}\\\"}]}]}\"\n"
            + "      }\n"
            + "  ]\n"
            + "}";

        return registerAndDeploy(connectorEntity, "bedrock-converse-streaming");
    }

    /** Creates a connector, registers a remote model against it, deploys it, and returns the model id. */
    private String registerAndDeploy(String connectorEntity, String modelName) throws Exception {
        Response response = RestMLRemoteInferenceIT.createConnector(connectorEntity);
        String connectorId = (String) parseResponseToMap(response).get("connector_id");

        response = RestMLRemoteInferenceIT.registerRemoteModel(modelName, connectorId);
        String taskId = (String) parseResponseToMap(response).get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);
        response = RestMLRemoteInferenceIT.getTask(taskId);
        String modelId = (String) parseResponseToMap(response).get("model_id");

        response = RestMLRemoteInferenceIT.deployRemoteModel(modelId);
        taskId = (String) parseResponseToMap(response).get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        return modelId;
    }

    /**
     * Collects streamed responses from a gRPC server-streaming call and exposes the terminal state.
     */
    private static class StreamCollector implements StreamObserver<PredictResponse> {
        private final List<PredictResponse> responses = new ArrayList<>();
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private volatile boolean completed = false;

        @Override
        public synchronized void onNext(PredictResponse value) {
            responses.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error.set(t);
            done.countDown();
        }

        @Override
        public void onCompleted() {
            completed = true;
            done.countDown();
        }

        boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return done.await(timeout, unit);
        }

        synchronized List<PredictResponse> responses() {
            return new ArrayList<>(responses);
        }

        Throwable error() {
            return error.get();
        }

        boolean completed() {
            return completed;
        }
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.opensearch.ml.engine.algorithms.agent.tracing.AgentSemanticAttributes.*;

import java.io.IOException;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.extern.log4j.Log4j2;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

/**
 * OpenTelemetry-based tracer for ML Commons agents.
 * Exports traces to OSIS (OpenSearch Ingestion Service) pipeline.
 *
 * Usage:
 * <pre>
 * // Initialize once at startup
 * AgentTracer.initialize("your-osis-endpoint.us-east-1.osis.amazonaws.com", "ml-commons");
 *
 * // In agent code
 * Span agentSpan = AgentTracer.startAgentSpan("ChatAgent", sessionId, parentInteractionId);
 * try (Scope scope = agentSpan.makeCurrent()) {
 *     // Agent logic...
 *     Span llmSpan = AgentTracer.startLlmSpan(agentSpan, modelId, iteration);
 *     try {
 *         // LLM call...
 *         AgentTracer.endSpan(llmSpan, true, response);
 *     } catch (Exception e) {
 *         AgentTracer.endSpanWithError(llmSpan, e);
 *     }
 *     AgentTracer.endSpan(agentSpan, true, finalAnswer);
 * }
 * </pre>
 */
@Log4j2
@SuppressWarnings("removal") // AccessController is deprecated but required for OpenSearch security manager
public final class AgentTracer {

    private static final String TRACER_NAME = "ml-commons-agent-tracer";
    private static final String TRACER_VERSION = "1.0.0";
    private static final String OSIS_SERVICE_NAME = "osis";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final Pattern REGION_PATTERN = Pattern.compile("\\.([a-z]{2}-[a-z]+-\\d)\\.osis\\.amazonaws\\.com");

    private static volatile OpenTelemetrySdk sdk;
    private static volatile Tracer tracer;
    private static volatile boolean initialized = false;
    private static volatile boolean enabled = true;

    private AgentTracer() {}

    /**
     * Extract AWS region from OSIS endpoint URL.
     * Example: agent-logs-xxx.us-east-1.osis.amazonaws.com -> us-east-1
     */
    private static String extractRegionFromEndpoint(String endpoint) {
        log.debug("[AgentTracer] Extracting region from endpoint: {}", endpoint);
        Matcher matcher = REGION_PATTERN.matcher(endpoint);
        if (matcher.find()) {
            String region = matcher.group(1);
            log.debug("[AgentTracer] Extracted region: {}", region);
            return region;
        }
        log.debug("[AgentTracer] Using default region: {}", DEFAULT_REGION);
        return DEFAULT_REGION;
    }

    /**
     * Initialize the tracer with OSIS endpoint and AWS credentials.
     *
     * @param osisEndpoint OSIS pipeline endpoint (e.g., "xxx.us-east-1.osis.amazonaws.com")
     * @param serviceName  Service name for traces (e.g., "ml-commons")
     * @param accessKey    AWS access key ID
     * @param secretKey    AWS secret access key
     * @param sessionToken AWS session token (optional, can be null or empty)
     * @param region       AWS region (optional, can be null to extract from endpoint)
     */
    public static synchronized void initialize(
        String osisEndpoint,
        String serviceName,
        String accessKey,
        String secretKey,
        String sessionToken,
        String region
    ) {
        log.info("[AgentTracer] initialize() called with endpoint: {}, serviceName: {}", osisEndpoint, serviceName);

        if (initialized) {
            log.debug("[AgentTracer] Already initialized, skipping");
            return;
        }

        if (osisEndpoint == null || osisEndpoint.isEmpty()) {
            log.warn("[AgentTracer] OSIS endpoint not configured, tracing disabled");
            enabled = false;
            initialized = true;
            return;
        }

        // Validate AWS credentials
        if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            log
                .warn(
                    "[AgentTracer] AWS credentials not configured, tracing disabled. "
                        + "Configure plugins.ml_commons.agent_tracing.aws_access_key and aws_secret_key to enable tracing."
                );
            enabled = false;
            initialized = true;
            return;
        }

        try {
            String endpoint = normalizeEndpoint(osisEndpoint);
            // Use provided region or extract from endpoint
            String effectiveRegion = (region != null && !region.isEmpty()) ? region : extractRegionFromEndpoint(osisEndpoint);
            log.debug("[AgentTracer] Normalized endpoint: {}, region: {}", endpoint, effectiveRegion);

            // Create AWS credentials provider from explicit credentials
            log.debug("[AgentTracer] Creating AWS credentials provider from explicit credentials...");
            final AwsCredentials credentials = (sessionToken != null && !sessionToken.isEmpty())
                ? AwsSessionCredentials.create(accessKey, secretKey, sessionToken)
                : AwsBasicCredentials.create(accessKey, secretKey);
            final AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
            log
                .debug(
                    "[AgentTracer] AWS credentials provider created (access key: {}...)",
                    accessKey.substring(0, Math.min(4, accessKey.length()))
                );

            // Create OkHttp client with SigV4 signing interceptor
            log.debug("[AgentTracer] Creating OkHttp client with SigV4 interceptor for service: {}", OSIS_SERVICE_NAME);
            OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(new SigV4SigningInterceptor(credentialsProvider, effectiveRegion, OSIS_SERVICE_NAME))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
            log.debug("[AgentTracer] OkHttp client created with SigV4 signing");

            // Create custom OTLP span exporter using OkHttp with SigV4 signing
            log.debug("[AgentTracer] Creating custom OTLP span exporter with SigV4 signing...");
            SigV4OtlpSpanExporter exporter = new SigV4OtlpSpanExporter(httpClient, endpoint);
            log.debug("[AgentTracer] Custom OTLP span exporter created");

            // Create resource with service attributes
            log.debug("[AgentTracer] Creating resource with service attributes...");
            Resource resource = Resource
                .getDefault()
                .merge(
                    Resource
                        .create(
                            Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName, ResourceAttributes.SERVICE_VERSION, TRACER_VERSION)
                        )
                );

            // Create tracer provider with batch processor
            log.debug("[AgentTracer] Creating tracer provider with batch processor...");
            SdkTracerProvider tracerProvider = SdkTracerProvider
                .builder()
                .addSpanProcessor(
                    BatchSpanProcessor
                        .builder(exporter)
                        .setMaxQueueSize(2048)
                        .setMaxExportBatchSize(32)  // Reduced to prevent payload too large errors
                        .setScheduleDelay(1, TimeUnit.SECONDS)
                        .setExporterTimeout(30, TimeUnit.SECONDS)
                        .build()
                )
                .setResource(resource)
                .build();
            log.debug("[AgentTracer] Tracer provider created");

            // Build SDK
            log.debug("[AgentTracer] Building OpenTelemetry SDK...");
            sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

            tracer = sdk.getTracer(TRACER_NAME, TRACER_VERSION);
            initialized = true;
            enabled = true;

            log.info("[AgentTracer] *** AgentTracer initialized with OSIS endpoint: {} (region: {}) ***", endpoint, effectiveRegion);

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(AgentTracer::shutdown));

        } catch (Exception e) {
            log.error("[AgentTracer] Failed to initialize AgentTracer", e);
            enabled = false;
            initialized = true;
        }
    }

    /**
     * SigV4 signing interceptor for OkHttp requests to OSIS.
     * Signs requests using AWS Signature Version 4 with service name 'osis'.
     */
    private static class SigV4SigningInterceptor implements Interceptor {
        private final AwsCredentialsProvider credentialsProvider;
        private final String region;
        private final String serviceName;
        private final Aws4Signer signer;

        SigV4SigningInterceptor(AwsCredentialsProvider credentialsProvider, String region, String serviceName) {
            this.credentialsProvider = credentialsProvider;
            this.region = region;
            this.serviceName = serviceName;
            this.signer = Aws4Signer.create();
            log.debug("[SigV4Interceptor] Created for region: {}, service: {}", region, serviceName);
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            log.debug("[SigV4Interceptor] Intercepting request to: {}", originalRequest.url());

            try {
                // Get AWS credentials with privileged access to read credentials files
                AwsCredentials credentials = AccessController
                    .doPrivileged((PrivilegedAction<AwsCredentials>) credentialsProvider::resolveCredentials);
                log.debug("[SigV4Interceptor] Resolved AWS credentials");

                // Build SDK HTTP request
                URI uri = originalRequest.url().uri();
                SdkHttpFullRequest.Builder sdkRequestBuilder = SdkHttpFullRequest
                    .builder()
                    .uri(uri)
                    .method(SdkHttpMethod.fromValue(originalRequest.method()));

                // Copy headers
                originalRequest.headers().forEach(pair -> sdkRequestBuilder.putHeader(pair.getFirst(), pair.getSecond()));

                // Add host header if not present
                if (originalRequest.header("Host") == null) {
                    sdkRequestBuilder.putHeader("Host", uri.getHost());
                }

                // Get request body
                final byte[] bodyBytes;
                RequestBody body = originalRequest.body();
                if (body != null) {
                    Buffer buffer = new Buffer();
                    body.writeTo(buffer);
                    bodyBytes = buffer.readByteArray();
                    final byte[] finalBodyBytes = bodyBytes;
                    sdkRequestBuilder.contentStreamProvider(() -> new java.io.ByteArrayInputStream(finalBodyBytes));
                } else {
                    bodyBytes = new byte[0];
                }

                SdkHttpFullRequest sdkRequest = sdkRequestBuilder.build();

                // Create signing parameters
                Aws4SignerParams signingParams = Aws4SignerParams
                    .builder()
                    .signingRegion(Region.of(region))
                    .signingName(serviceName)
                    .awsCredentials(credentials)
                    .build();

                // Sign the request
                SdkHttpFullRequest signedRequest = signer.sign(sdkRequest, signingParams);
                log.debug("[SigV4Interceptor] Request signed successfully");

                // Build new OkHttp request with signed headers
                Request.Builder newRequestBuilder = originalRequest.newBuilder();
                signedRequest.headers().forEach((name, values) -> {
                    for (String value : values) {
                        newRequestBuilder.header(name, value);
                    }
                });

                // Restore body if present
                if (bodyBytes.length > 0 && body != null) {
                    MediaType mediaType = body.contentType();
                    newRequestBuilder.method(originalRequest.method(), RequestBody.create(bodyBytes, mediaType));
                }

                Request signedOkHttpRequest = newRequestBuilder.build();
                log.debug("[SigV4Interceptor] Proceeding with signed request");
                return chain.proceed(signedOkHttpRequest);

            } catch (Exception e) {
                log.error("[SigV4Interceptor] Failed to sign request", e);
                // Don't fall back to unsigned request - throw exception to fail the export
                throw new IOException("Failed to sign request with SigV4: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Custom SpanExporter that sends OTLP spans via OkHttp with SigV4 signing.
     * This is necessary because the standard OtlpHttpSpanExporter doesn't support
     * custom HTTP clients with SigV4 authentication.
     */
    private static class SigV4OtlpSpanExporter implements SpanExporter {
        private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
        private final OkHttpClient httpClient;
        private final String endpoint;

        SigV4OtlpSpanExporter(OkHttpClient httpClient, String endpoint) {
            this.httpClient = httpClient;
            this.endpoint = endpoint;
            log.debug("[SigV4OtlpExporter] Created with endpoint: {}", endpoint);
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            if (spans.isEmpty()) {
                return CompletableResultCode.ofSuccess();
            }

            log.info("[SigV4OtlpExporter] Exporting {} spans to OSIS endpoint", spans.size());
            CompletableResultCode result = new CompletableResultCode();

            try {
                // Serialize spans to OTLP JSON format (OSIS accepts both JSON and protobuf)
                byte[] serializedSpans = serializeSpansToJson(spans);
                log.debug("[SigV4OtlpExporter] Serialized {} bytes to endpoint: {}", serializedSpans.length, endpoint);

                // Build HTTP request
                Request request = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(serializedSpans, JSON_MEDIA_TYPE))
                    .addHeader("Content-Type", "application/json")
                    .build();

                // Execute request (SigV4 signing happens in interceptor)
                httpClient.newCall(request).enqueue(new okhttp3.Callback() {
                    @Override
                    public void onFailure(okhttp3.Call call, IOException e) {
                        log.error("[SigV4OtlpExporter] Failed to export spans", e);
                        result.fail();
                    }

                    @Override
                    public void onResponse(okhttp3.Call call, Response response) throws IOException {
                        try {
                            if (response.isSuccessful()) {
                                log.info("[SigV4OtlpExporter] Successfully exported spans (status: {})", response.code());
                                result.succeed();
                            } else {
                                String body = response.body() != null ? response.body().string() : "no body";
                                log.error("[SigV4OtlpExporter] Export failed with status {}: {}", response.code(), body);
                                result.fail();
                            }
                        } finally {
                            response.close();
                        }
                    }
                });

            } catch (Exception e) {
                log.error("[SigV4OtlpExporter] Failed to serialize or export spans", e);
                result.fail();
            }

            return result;
        }

        private byte[] serializeSpansToJson(Collection<SpanData> spans) {
            // Serialize spans to OTLP JSON format
            // OSIS accepts both JSON and protobuf formats
            StringBuilder json = new StringBuilder();
            json
                .append("{\"resourceSpans\":[{\"resource\":{},\"scopeSpans\":[{\"scope\":{\"name\":\"")
                .append(TRACER_NAME)
                .append("\"},\"spans\":[");

            boolean first = true;
            for (SpanData span : spans) {
                if (!first)
                    json.append(",");
                first = false;
                json.append("{\"traceId\":\"").append(span.getTraceId()).append("\"");
                json.append(",\"spanId\":\"").append(span.getSpanId()).append("\"");
                json.append(",\"name\":\"").append(escapeJson(span.getName())).append("\"");
                json.append(",\"kind\":").append(span.getKind().ordinal() + 1);
                json.append(",\"startTimeUnixNano\":").append(span.getStartEpochNanos());
                json.append(",\"endTimeUnixNano\":").append(span.getEndEpochNanos());

                // Add attributes
                json.append(",\"attributes\":[");
                boolean firstAttr = true;
                for (var entry : span.getAttributes().asMap().entrySet()) {
                    if (!firstAttr)
                        json.append(",");
                    firstAttr = false;
                    json.append("{\"key\":\"").append(escapeJson(entry.getKey().getKey())).append("\"");
                    json.append(",\"value\":{\"stringValue\":\"").append(escapeJson(String.valueOf(entry.getValue()))).append("\"}}");
                }
                json.append("]");

                // Add status
                json.append(",\"status\":{\"code\":").append(span.getStatus().getStatusCode().ordinal()).append("}");

                json.append("}");
            }

            json.append("]}]}]}");
            return json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        private String escapeJson(String value) {
            if (value == null)
                return "";
            return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }

        @Override
        public CompletableResultCode flush() {
            log.debug("[SigV4OtlpExporter] Flush called");
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            log.debug("[SigV4OtlpExporter] Shutdown called");
            return CompletableResultCode.ofSuccess();
        }
    }

    /**
     * Check if tracing is enabled.
     */
    public static boolean isEnabled() {
        return enabled && initialized;
    }

    /**
     * Get the tracer instance.
     */
    public static Tracer getTracer() {
        return tracer;
    }

    // ============ Agent Span Methods ============

    /**
     * Start a root span for agent execution.
     * Use this for both ChatAgent and PlanExecuteReflectAgent.
     *
     * @param agentType Type of agent (e.g., "ChatAgent", "PlanExecuteReflectAgent")
     * @param sessionId Session/conversation ID (memory_id)
     * @param runId AG-UI run ID for frontend correlation (from AGUI_PARAM_RUN_ID)
     */
    public static Span startAgentSpan(String agentType, String sessionId, String runId) {
        log.info("[AgentTracer] startAgentSpan called: enabled={}, initialized={}, tracer={}", enabled, initialized, tracer != null);
        if (!isEnabled()) {
            log
                .info(
                    "[AgentTracer] Tracing DISABLED (not initialized) - would have created span with: type={}, sessionId={}, runId={}",
                    agentType,
                    sessionId,
                    runId
                );
            return Span.getInvalid();
        }

        log.info("[AgentTracer] Starting agent span: type={}, sessionId={}, runId={}", agentType, sessionId, runId);
        return tracer
            .spanBuilder(SpanNames.AGENT_RUN)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute(AGENT_TYPE, agentType)
            .setAttribute(CONVERSATION_ID, sessionId != null ? sessionId : "")
            .setAttribute(REQUEST_ID, runId != null ? runId : "")
            .startSpan();
    }

    /**
     * Start an LLM inference span.
     *
     * @param parent Parent agent span
     * @param modelId Model ID being used
     * @param iteration Current iteration number
     * @param runId AG-UI run ID for frontend correlation
     */
    public static Span startLlmSpan(Span parent, String modelId, int iteration, String runId) {
        if (!isEnabled())
            return Span.getInvalid();

        SpanBuilder builder = tracer
            .spanBuilder(SpanNames.LLM_INFERENCE)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(MODEL, modelId != null ? modelId : "")
            .setAttribute(AGENT_ITERATION, (long) iteration)
            .setAttribute(SYSTEM, "aws_bedrock")
            .setAttribute(REQUEST_ID, runId != null ? runId : "");

        if (parent != null && parent.getSpanContext().isValid()) {
            builder.setParent(Context.current().with(parent));
        }

        return builder.startSpan();
    }

    /**
     * Start a tool execution span.
     *
     * @param parent Parent agent span
     * @param toolName Name of the tool being executed
     * @param toolInput Tool input parameters
     * @param runId AG-UI run ID for frontend correlation
     */
    public static Span startToolSpan(Span parent, String toolName, String toolInput, String runId) {
        if (!isEnabled())
            return Span.getInvalid();

        SpanBuilder builder = tracer
            .spanBuilder(SpanNames.TOOL_EXECUTE + "." + toolName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(TOOL_NAME, toolName)
            .setAttribute(TOOL_INPUT, truncate(toolInput))
            .setAttribute(REQUEST_ID, runId != null ? runId : "");

        if (parent != null && parent.getSpanContext().isValid()) {
            builder.setParent(Context.current().with(parent));
        }

        return builder.startSpan();
    }

    /**
     * Start a step execution span (for PER agent).
     */
    public static Span startStepSpan(Span parent, int stepNumber, String stepDescription) {
        if (!isEnabled())
            return Span.getInvalid();

        SpanBuilder builder = tracer
            .spanBuilder(SpanNames.STEP_EXECUTE)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(STEP_NUMBER, (long) stepNumber)
            .setAttribute(STEP_DESCRIPTION, truncate(stepDescription, 500));

        if (parent != null && parent.getSpanContext().isValid()) {
            builder.setParent(Context.current().with(parent));
        }

        return builder.startSpan();
    }

    /**
     * Start a planning phase span (for PER agent).
     */
    public static Span startPlanningSpan(Span parent, String modelId) {
        if (!isEnabled())
            return Span.getInvalid();

        SpanBuilder builder = tracer
            .spanBuilder(SpanNames.PLANNING)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(MODEL, modelId != null ? modelId : "");

        if (parent != null && parent.getSpanContext().isValid()) {
            builder.setParent(Context.current().with(parent));
        }

        return builder.startSpan();
    }

    // ============ Span Event Methods ============

    /**
     * Add an LLM request event to a span.
     */
    public static void addLlmRequestEvent(Span span, String prompt, int toolCount) {
        if (!isEnabled() || span == null)
            return;

        span
            .addEvent(
                EventNames.LLM_REQUEST,
                Attributes.builder().put("llm.prompt", truncate(prompt)).put("llm.tool_count", toolCount).build()
            );
    }

    /**
     * Add an LLM response event to a span.
     */
    public static void addLlmResponseEvent(Span span, String response, String stopReason, long inputTokens, long outputTokens) {
        if (!isEnabled() || span == null)
            return;

        span
            .addEvent(
                EventNames.LLM_RESPONSE,
                Attributes
                    .builder()
                    .put("llm.completion", truncate(response))
                    .put("llm.stop_reason", stopReason != null ? stopReason : "")
                    .put(INPUT_TOKENS.getKey(), inputTokens)
                    .put(OUTPUT_TOKENS.getKey(), outputTokens)
                    .build()
            );
    }

    /**
     * Add a tool output event to a span.
     */
    public static void addToolOutputEvent(Span span, String toolResult, long durationMs) {
        if (!isEnabled() || span == null)
            return;

        span
            .addEvent(
                EventNames.TOOL_OUTPUT,
                Attributes.builder().put("tool.result", truncate(toolResult)).put(TOOL_DURATION_MS.getKey(), durationMs).build()
            );
    }

    // ============ Span Completion Methods ============

    /**
     * End a span successfully.
     */
    public static void endSpan(Span span, boolean success, String output) {
        log.info("[AgentTracer] endSpan called: span={}, success={}", span != null ? span.getSpanContext().getSpanId() : "null", success);
        if (span == null || !span.getSpanContext().isValid()) {
            log.warn("[AgentTracer] endSpan: span is null or invalid, skipping");
            return;
        }

        span.setAttribute(RESULT_SUCCESS, success);
        if (output != null) {
            span.setAttribute(RESULT_OUTPUT, truncate(output, 500));
        }
        span.setStatus(success ? StatusCode.OK : StatusCode.ERROR);
        span.end();
        log.info("[AgentTracer] endSpan: span ended successfully, spanId={}", span.getSpanContext().getSpanId());
    }

    /**
     * End a span with an error.
     */
    public static void endSpanWithError(Span span, Throwable error) {
        log
            .info(
                "[AgentTracer] endSpanWithError called: span={}, error={}",
                span != null ? span.getSpanContext().getSpanId() : "null",
                error.getMessage()
            );
        if (span == null || !span.getSpanContext().isValid()) {
            log.warn("[AgentTracer] endSpanWithError: span is null or invalid, skipping");
            return;
        }

        span.setAttribute(RESULT_SUCCESS, false);
        span.setAttribute(ERROR_MESSAGE, error.getMessage());
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
        span.end();
        log.info("[AgentTracer] endSpanWithError: span ended with error, spanId={}", span.getSpanContext().getSpanId());
    }

    /**
     * Make a span current and return a scope for try-with-resources.
     */
    public static Scope makeCurrent(Span span) {
        if (span == null)
            return () -> {};
        return span.makeCurrent();
    }

    // ============ Utility Methods ============

    private static String normalizeEndpoint(String endpoint) {
        String normalized = endpoint;
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            normalized = "https://" + normalized;
        }
        if (!normalized.endsWith("/v1/traces")) {
            if (!normalized.endsWith("/")) {
                normalized += "/";
            }
            normalized += "v1/traces";
        }
        return normalized;
    }

    /**
     * Shutdown the tracer and flush pending spans.
     */
    public static synchronized void shutdown() {
        if (sdk != null) {
            try {
                sdk.getSdkTracerProvider().shutdown();
                log.info("AgentTracer shut down successfully");
            } catch (Exception e) {
                log.error("Error shutting down AgentTracer", e);
            }
        }
    }

    /**
     * Reset the tracer (for testing purposes only).
     */
    public static synchronized void reset() {
        shutdown();
        sdk = null;
        tracer = null;
        initialized = false;
        enabled = true;
    }
}

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

import lombok.extern.log4j.Log4j2;

/**
 * MLModelTracer is a concrete implementation of MLTracer for model tracing in ML Commons.
 * It manages the lifecycle of model-related spans, including creation, context propagation, and completion.
 * 
 * <p>This class is implemented as a singleton to ensure that only one tracer is active
 * for model tracing at any time. This design provides consistent management of tracing state and configuration,
 * and avoids issues with multiple tracers being active at once.
 * The singleton can be dynamically enabled or disabled based on cluster settings.</p>
 * 
 * <p>This class is thread-safe: multiple threads can use the singleton instance to start and end spans concurrently.
 * Each call to {@link #startSpan(String, Map, Span)} creates a new, independent span.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Model operation tracing (predict, execute)</li>
 *   <li>Dynamic tracing enable/disable based on cluster settings</li>
 *   <li>Error handling with span context preservation</li>
 * </ul>
 */
@Log4j2
public class MLModelTracer extends MLTracer {
    public static final String SERVICE_TYPE = "service.type";
    public static final String ML_MODEL_ID = "ml.model.id";
    public static final String ML_MODEL_NAME = "ml.model.name";
    public static final String ML_MODEL_REQUEST_BODY = "ml.model.request_body";

    public static final String MODEL_PREDICT_SPAN = "model.predict";
    public static final String MODEL_EXECUTE_SPAN = "model.execute";

    public static final String SERVICE_TYPE_TRACER = "tracer";

    private static MLModelTracer instance;

    private MLModelTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(tracer, mlFeatureEnabledSetting);
    }

    /**
     * Initializes the singleton MLModelTracer instance with the given tracer and settings.
     * This is a convenience method that calls the full initialize method with a null ClusterService.
     *
     * @param tracer The tracer implementation to use. If null or if tracing is disabled,
     *               a NoopTracer will be used instead.
     * @param mlFeatureEnabledSetting The ML feature settings that control tracing behavior.
     *                                If null, tracing will be disabled.
     */
    public static synchronized void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        initialize(tracer, mlFeatureEnabledSetting, null);
    }

    /**
     * Initializes the singleton MLModelTracer instance with the given tracer and settings.
     * If model tracing is disabled, a NoopTracer is used.
     * @param tracer The tracer implementation to use. If null or if tracing is disabled,
     *               a NoopTracer will be used instead.
     * @param mlFeatureEnabledSetting The ML feature settings that control tracing behavior.
     *                                If null, tracing will be disabled.
     * @param clusterService The cluster service for dynamic settings updates. May be null.
     */
    public static synchronized void initialize(
        Tracer tracer,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ClusterService clusterService
    ) {
        Tracer tracerToUse = (mlFeatureEnabledSetting != null
            && mlFeatureEnabledSetting.isTracingEnabled()
            && mlFeatureEnabledSetting.isModelTracingEnabled()
            && tracer != null) ? tracer : NoopTracer.INSTANCE;

        instance = new MLModelTracer(tracerToUse, mlFeatureEnabledSetting);
        log.info("MLModelTracer initialized with {}", tracerToUse.getClass().getSimpleName());

        if (clusterService != null) {
            clusterService.getClusterSettings().addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_MODEL_TRACING_ENABLED, enabled -> {
                Tracer newTracerToUse = (mlFeatureEnabledSetting != null
                    && mlFeatureEnabledSetting.isTracingEnabled()
                    && enabled
                    && tracer != null) ? tracer : NoopTracer.INSTANCE;
                instance = new MLModelTracer(newTracerToUse, mlFeatureEnabledSetting);
                log.info("MLModelTracer re-initialized with {} due to setting change", newTracerToUse.getClass().getSimpleName());
            });
        }
    }

    /**
     * Returns the singleton MLModelTracer instance.
     * @return The MLModelTracer instance.
     * @throws IllegalStateException if the tracer is not initialized.
     */
    public static synchronized MLModelTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLModelTracer is not initialized. Call initialize() first before using getInstance().");
        }
        return instance;
    }

    /**
     * Starts a span for model predict operations.
     * @param modelId The ID of the model being used for prediction.
     * @param modelName The name of the model being used for prediction.
     * @return A Span object representing the model predict span.
     */
    public static Span startModelPredictSpan(String modelId, String modelName) {
        return getInstance().startSpan(MODEL_PREDICT_SPAN, createModelAttributes(modelId, modelName));
    }

    /**
     * Starts a span for model execute operations.
     * @param modelId The ID of the model being executed.
     * @param modelName The name of the model being executed.
     * @return A Span object representing the model execute span.
     */
    public static Span startModelExecuteSpan(String modelId, String modelName) {
        return getInstance().startSpan(MODEL_EXECUTE_SPAN, createModelAttributes(modelId, modelName));
    }

    /**
     * Handles span errors by logging, setting span error, and ending the span.
     * @param span The span to handle the error for.
     * @param errorMessage The error message to log.
     * @param e The exception that occurred.
     */
    public static void handleSpanError(Span span, String errorMessage, Exception e) {
        log.error(errorMessage, e);
        span.setError(e);
        getInstance().endSpan(span);
    }

    /**
     * Creates attributes for model spans.
     * @param modelId The ID of the model.
     * @param modelName The name of the model.
     * @return A map of attributes for the model span.
     */
    public static Map<String, String> createModelAttributes(String modelId, String modelName) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, SERVICE_TYPE_TRACER);
        if (modelId != null)
            attributes.put(ML_MODEL_ID, modelId);
        if (modelName != null)
            attributes.put(ML_MODEL_NAME, modelName);
        return attributes;
    }

    /**
     * Serializes the model input for tracing purposes.
     * @param mlPredictionTaskRequest The prediction task request to serialize.
     * @param predictSpan The span to add the serialized input to.
     */
    public static void serializeInputForTracing(MLPredictionTaskRequest mlPredictionTaskRequest, Span predictSpan) {
        if (mlPredictionTaskRequest.getMlInput() != null) {
            try {
                String inputBody = mlPredictionTaskRequest
                    .getMlInput()
                    .toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS)
                    .toString();
                predictSpan.addAttribute(MLModelTracer.ML_MODEL_REQUEST_BODY, inputBody);
            } catch (Exception e) {
                log.warn("Failed to serialize model input for tracing", e);
            }
        }
    }

    /**
     * Resets the singleton instance for testing purposes.
     * This method should only be used in unit tests to ensure a clean state.
     */
    @VisibleForTesting
    public static void resetForTest() {
        instance = null;
    }
}

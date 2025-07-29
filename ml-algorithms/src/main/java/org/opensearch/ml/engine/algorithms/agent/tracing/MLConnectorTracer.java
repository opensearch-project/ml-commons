package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

import lombok.extern.log4j.Log4j2;

/**
 * MLConnectorTracer is a concrete implementation of MLTracer for connector tracing in ML Commons.
 * It manages the lifecycle of connector-related spans, including creation, context propagation, and completion.
 * 
 * <p>This class is implemented as a singleton to ensure that only one tracer is active
 * for connector tracing at any time. This design provides consistent management of tracing state and configuration,
 * and avoids issues with multiple tracers being active at once.
 * The singleton can be dynamically enabled or disabled based on cluster settings.</p>
 * 
 * <p>This class is thread-safe: multiple threads can use the singleton instance to start and end spans concurrently.
 * Each call to {@link #startSpan(String, Map, Span)} creates a new, independent span.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Connector operation tracing (create, read, update, delete)</li>
 *   <li>Model operation tracing (predict, execute)</li>
 *   <li>Dynamic tracing enable/disable based on cluster settings</li>
 *   <li>Error handling with span context preservation</li>
 * </ul>
 */
@Log4j2
public class MLConnectorTracer extends MLTracer {
    public static final String SERVICE_TYPE = "service.type";
    public static final String ML_CONNECTOR_ID = "ml.connector.id";
    public static final String ML_CONNECTOR_NAME = "ml.connector.name";
    public static final String ML_MODEL_ID = "ml.model.id";
    public static final String ML_MODEL_NAME = "ml.model.name";
    public static final String ML_MODEL_REQUEST_BODY = "ml.model.request_body";

    public static final String CONNECTOR_CREATE_SPAN = "connector.create";
    public static final String CONNECTOR_READ_SPAN = "connector.read";
    public static final String CONNECTOR_UPDATE_SPAN = "connector.update";
    public static final String CONNECTOR_DELETE_SPAN = "connector.delete";
    public static final String MODEL_PREDICT_SPAN = "model.predict";
    public static final String MODEL_EXECUTE_SPAN = "model.execute";

    private static MLConnectorTracer instance;

    private MLConnectorTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(tracer, mlFeatureEnabledSetting);
    }

    /**
     * Initializes the singleton MLConnectorTracer instance with the given tracer and settings.
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
     * Initializes the singleton MLConnectorTracer instance with the given tracer and settings.
     * If connector tracing is disabled, a NoopTracer is used.
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
            && mlFeatureEnabledSetting.isConnectorTracingEnabled()
            && tracer != null) ? tracer : NoopTracer.INSTANCE;

        instance = new MLConnectorTracer(tracerToUse, mlFeatureEnabledSetting);
        log.info("MLConnectorTracer initialized with {}", tracerToUse.getClass().getSimpleName());

        if (clusterService != null) {
            clusterService
                .getClusterSettings()
                .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_CONNECTOR_TRACING_ENABLED, enabled -> {
                    Tracer newTracerToUse = (mlFeatureEnabledSetting != null
                        && mlFeatureEnabledSetting.isTracingEnabled()
                        && enabled
                        && tracer != null) ? tracer : NoopTracer.INSTANCE;
                    instance = new MLConnectorTracer(newTracerToUse, mlFeatureEnabledSetting);
                    log.info("MLConnectorTracer re-initialized with {} due to setting change", newTracerToUse.getClass().getSimpleName());
                });
        }
    }

    /**
     * Returns the singleton MLConnectorTracer instance.
     * @return The MLConnectorTracer instance.
     * @throws IllegalStateException if the tracer is not initialized.
     */
    public static synchronized MLConnectorTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLConnectorTracer is not initialized. Call initialize() first before using getInstance().");
        }
        return instance;
    }

    /**
     * Starts a span for connector creation operations.
     * @param connectorName The name of the connector being created.
     * @return A Span object representing the connector creation span.
     */
    public static Span startConnectorCreateSpan(String connectorName) {
        return getInstance().startSpan(CONNECTOR_CREATE_SPAN, createConnectorAttributes(null, connectorName));
    }

    /**
     * Starts a span for connector read operations.
     * @param connectorId The ID of the connector being read.
     * @return A Span object representing the connector read span.
     */
    public static Span startConnectorReadSpan(String connectorId) {
        return getInstance().startSpan(CONNECTOR_READ_SPAN, createConnectorAttributes(connectorId, null));
    }

    /**
     * Starts a span for connector update operations.
     * @param connectorId The ID of the connector being updated.
     * @return A Span object representing the connector update span.
     */
    public static Span startConnectorUpdateSpan(String connectorId) {
        return getInstance().startSpan(CONNECTOR_UPDATE_SPAN, createConnectorAttributes(connectorId, null));
    }

    /**
     * Starts a span for connector delete operations.
     * @param connectorId The ID of the connector being deleted.
     * @return A Span object representing the connector delete span.
     */
    public static Span startConnectorDeleteSpan(String connectorId) {
        return getInstance().startSpan(CONNECTOR_DELETE_SPAN, createConnectorAttributes(connectorId, null));
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
     * Handles span error by logging, setting error on span, ending span, and failing listener.
     * @param span The span to handle error for.
     * @param errorMessage The error message to log.
     * @param e The exception that occurred.
     * @param listener The action listener to fail.
     */
    public static void handleSpanError(Span span, String errorMessage, Exception e, ActionListener<?> listener) {
        log.error(errorMessage, e);
        span.setError(e);
        getInstance().endSpan(span);
        if (listener != null) {
            listener.onFailure(e);
        }
    }

    /**
     * Ends the span and responds to the listener with the result.
     * @param span The span to end.
     * @param result The result to send to the listener.
     * @param listener The action listener to respond to.
     */
    public static <T> void endSpanAndRespond(Span span, T result, ActionListener<T> listener) {
        getInstance().endSpan(span);
        if (listener != null) {
            listener.onResponse(result);
        }
    }

    /**
     * Creates attributes for connector spans.
     * @param connectorId The ID of the connector.
     * @param connectorName The name of the connector.
     * @return A map of attributes for the connector span.
     */
    public static Map<String, String> createConnectorAttributes(String connectorId, String connectorName) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (connectorId != null)
            attributes.put(ML_CONNECTOR_ID, connectorId);
        if (connectorName != null)
            attributes.put(ML_CONNECTOR_NAME, connectorName);
        return attributes;
    }

    /**
     * Creates attributes for model spans.
     * @param modelId The ID of the model.
     * @param modelName The name of the model.
     * @return A map of attributes for the model span.
     */
    public static Map<String, String> createModelAttributes(String modelId, String modelName) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (modelId != null)
            attributes.put(ML_MODEL_ID, modelId);
        if (modelName != null)
            attributes.put(ML_MODEL_NAME, modelName);
        return attributes;
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

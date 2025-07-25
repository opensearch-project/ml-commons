package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

import lombok.extern.log4j.Log4j2;

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

    public static synchronized MLConnectorTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLConnectorTracer is not initialized. Call initialize() first before using getInstance().");
        }
        return instance;
    }

    public static Map<String, String> createConnectorAttributes(String connectorId, String connectorName) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (connectorId != null)
            attributes.put(ML_CONNECTOR_ID, connectorId);
        if (connectorName != null)
            attributes.put(ML_CONNECTOR_NAME, connectorName);
        return attributes;
    }

    public static Map<String, String> createModelAttributes(String modelId, String modelName) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (modelId != null)
            attributes.put(ML_MODEL_ID, modelId);
        if (modelName != null)
            attributes.put(ML_MODEL_NAME, modelName);
        return attributes;
    }
}

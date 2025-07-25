package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Tracer;

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

    public static void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        instance = new MLConnectorTracer(tracer, mlFeatureEnabledSetting);
    }

    public static MLConnectorTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLConnectorTracer is not initialized");
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

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Tracer;

public class MLConnectorTracer extends MLTracer {
    // Attribute key constants
    public static final String SERVICE_TYPE = "service.type";
    public static final String ML_CONNECTOR_NAME = "ml.connector.name";
    public static final String ML_CONNECTOR_TYPE = "ml.connector.type";
    public static final String ML_CONNECTOR_DRY_RUN = "ml.connector.dry_run";
    public static final String ML_CONNECTOR_VERSION = "ml.connector.version";
    public static final String ML_CONNECTOR_DESCRIPTION = "ml.connector.description";
    public static final String ML_CONNECTOR_PARAMETERS = "ml.connector.parameters";
    public static final String ML_INDEX_NAME = "ml.index.name";
    public static final String ML_MODEL_NAME = "ml.model.name";
    public static final String ML_MODEL_VERSION = "ml.model.version";
    public static final String ML_MODEL_FUNCTION_NAME = "ml.model.function_name";
    public static final String ML_TASK_TYPE = "ml.task.type";

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

    public static Map<String, String> createConnectorAttributes(
        String connectorName,
        String connectorType,
        Boolean dryRun,
        String version,
        String description,
        String parameters
    ) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (connectorName != null)
            attributes.put(ML_CONNECTOR_NAME, connectorName);
        if (connectorType != null)
            attributes.put(ML_CONNECTOR_TYPE, connectorType);
        if (dryRun != null)
            attributes.put(ML_CONNECTOR_DRY_RUN, String.valueOf(dryRun));
        if (version != null)
            attributes.put(ML_CONNECTOR_VERSION, version);
        if (description != null)
            attributes.put(ML_CONNECTOR_DESCRIPTION, description);
        if (parameters != null)
            attributes.put(ML_CONNECTOR_PARAMETERS, parameters);
        return attributes;
    }

    public static Map<String, String> createIndexAttributes(String indexName) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (indexName != null)
            attributes.put(ML_INDEX_NAME, indexName);
        return attributes;
    }

    public static Map<String, String> createModelRegisterAttributes(String modelName, String modelVersion, String functionName) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (modelName != null)
            attributes.put(ML_MODEL_NAME, modelName);
        if (modelVersion != null)
            attributes.put(ML_MODEL_VERSION, modelVersion);
        if (functionName != null)
            attributes.put(ML_MODEL_FUNCTION_NAME, functionName);
        return attributes;
    }

    public static Map<String, String> createModelIndexAttributes(
        String indexName,
        String modelName,
        String modelVersion,
        String functionName
    ) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (indexName != null)
            attributes.put(ML_INDEX_NAME, indexName);
        if (modelName != null)
            attributes.put(ML_MODEL_NAME, modelName);
        if (modelVersion != null)
            attributes.put(ML_MODEL_VERSION, modelVersion);
        if (functionName != null)
            attributes.put(ML_MODEL_FUNCTION_NAME, functionName);
        return attributes;
    }

    public static Map<String, String> createTaskAttributes(
        String taskType,
        String userName,
        String inputAlgorithm,
        String inputParameters
    ) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(SERVICE_TYPE, "tracer");
        if (taskType != null)
            attributes.put(ML_TASK_TYPE, taskType);
        if (userName != null)
            attributes.put("user.name", userName);
        if (inputAlgorithm != null)
            attributes.put("ml.input.algorithm", inputAlgorithm);
        if (inputParameters != null)
            attributes.put("ml.input.parameters", inputParameters);
        return attributes;
    }
}

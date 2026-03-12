/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.converters;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.protobufs.MlExecuteAgentStreamRequest;
import org.opensearch.protobufs.MlPredictModelStreamRequest;
import org.opensearch.protobufs.ObjectMap;

import lombok.extern.log4j.Log4j2;

/**
 * Converts protobuf request messages to ML Commons request objects.
 *
 * <p>This converter handles the mapping between gRPC proto messages and internal
 * ML Commons data structures for streaming requests.
 */
@Log4j2
public class ProtoRequestConverter {

    // Standard parameter keys expected in ObjectMap
    private static final String MODEL_ID_KEY = "model_id";
    private static final String AGENT_ID_KEY = "agent_id";
    private static final String TENANT_ID_KEY = "tenant_id";
    private static final String PROMPT_KEY = "prompt";
    private static final String QUESTION_KEY = "question";

    /**
     * Converts a MlPredictModelStreamRequest protobuf to MLPredictionTaskRequest.
     *
     * <p>Expected parameters in the ObjectMap:
     * <ul>
     *   <li>model_id (string, required) - The ID of the model to invoke
     *   <li>tenant_id (string, optional) - Multi-tenancy tenant ID
     *   <li>prompt (string, optional) - The input prompt for LLM models
     *   <li>question (string, optional) - Alternative to prompt
     *   <li>Any other parameters are passed through to the remote model
     * </ul>
     *
     * @param request the protobuf request
     * @return MLPredictionTaskRequest for internal processing
     * @throws IllegalArgumentException if required parameters are missing
     */
    public static MLPredictionTaskRequest toPredictRequest(MlPredictModelStreamRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("MlPredictModelStreamRequest cannot be null");
        }

        if (!request.hasParameters()) {
            throw new IllegalArgumentException("MlPredictModelStreamRequest must have parameters");
        }

        ObjectMap parametersMap = request.getParameters();
        Map<String, Object> params = objectMapToJavaMap(parametersMap);

        // Extract model_id (required)
        String modelId = extractString(params, MODEL_ID_KEY);
        if (modelId == null || modelId.isEmpty()) {
            throw new IllegalArgumentException("model_id is required in parameters");
        }

        // Extract tenant_id (optional)
        String tenantId = extractString(params, TENANT_ID_KEY);

        // Convert remaining parameters to string map for RemoteInferenceInputDataSet
        Map<String, String> remoteParams = convertToStringMap(params);

        // Debug logging
        log.info("ProtoRequestConverter: Converted parameters: {}", remoteParams.keySet());
        if (remoteParams.containsKey("_llm_interface")) {
            log.info("ProtoRequestConverter: _llm_interface = {}", remoteParams.get("_llm_interface"));
        } else {
            log.warn("ProtoRequestConverter: _llm_interface NOT FOUND in parameters!");
        }

        // Create MLInput with RemoteInferenceInputDataSet
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(remoteParams).build();

        MLInput mlInput = RemoteInferenceMLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();

        // Build the prediction task request
        // Note: Using simple constructor to avoid User dependency
        // TODO: Implement full constructor with user context and tenantId from gRPC security context
        // For now, tenantId is extracted but not passed (simple constructor doesn't support it)
        if (tenantId != null) {
            log.debug("Tenant ID provided but not yet supported in simple constructor: {}", tenantId);
        }

        return new MLPredictionTaskRequest(modelId, mlInput);
    }

    /**
     * Converts a MlExecuteAgentStreamRequest protobuf to MLExecuteTaskRequest.
     *
     * <p>Expected parameters:
     * <ul>
     *   <li>agent_id (string, required) - From the top-level agent_id field
     *   <li>tenant_id (string, optional) - From nested parameters
     *   <li>Additional parameters from ml_predict_model_stream_request if present
     * </ul>
     *
     * @param request the protobuf request
     * @return MLExecuteTaskRequest for internal processing
     * @throws IllegalArgumentException if required parameters are missing
     * @throws UnsupportedOperationException agent execution not yet fully implemented
     */
    public static MLExecuteTaskRequest toExecuteRequest(MlExecuteAgentStreamRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("MlExecuteAgentStreamRequest cannot be null");
        }

        String agentId = request.getAgentId();
        if (agentId == null || agentId.isEmpty()) {
            throw new IllegalArgumentException("agent_id is required");
        }

        // Extract parameters from nested predict request if present
        Map<String, Object> params = new HashMap<>();
        if (request.hasMlPredictModelStreamRequest() && request.getMlPredictModelStreamRequest().hasParameters()) {
            ObjectMap parametersMap = request.getMlPredictModelStreamRequest().getParameters();
            params = objectMapToJavaMap(parametersMap);
        }

        String tenantId = extractString(params, TENANT_ID_KEY);

        // TODO: Implement full agent execution request conversion
        // This requires understanding the agent input structure and flow tool parameters
        throw new UnsupportedOperationException(
            "Agent execution streaming not yet fully implemented. "
                + "Requires implementing conversion for agent-specific parameters. "
                + "Agent ID: "
                + agentId
                + ", Tenant ID: "
                + tenantId
        );
    }

    /**
     * Converts protobuf ObjectMap to Java Map.
     *
     * @param objectMap the protobuf ObjectMap
     * @return Java Map with converted values
     */
    private static Map<String, Object> objectMapToJavaMap(ObjectMap objectMap) {
        Map<String, Object> result = new HashMap<>();

        if (objectMap == null || objectMap.getFieldsMap() == null) {
            return result;
        }

        for (Map.Entry<String, ObjectMap.Value> entry : objectMap.getFieldsMap().entrySet()) {
            String key = entry.getKey();
            ObjectMap.Value value = entry.getValue();

            Object javaValue = convertProtoValue(value);
            if (javaValue != null) {
                result.put(key, javaValue);
            }
        }

        return result;
    }

    /**
     * Converts a single protobuf Value to Java object.
     *
     * @param value the protobuf Value
     * @return Java object (String, Integer, Long, Float, Double, Boolean, Map, List, or null)
     */
    private static Object convertProtoValue(ObjectMap.Value value) {
        if (value == null) {
            return null;
        }

        switch (value.getValueCase()) {
            case NULL_VALUE:
                return null;
            case INT32:
                return value.getInt32();
            case INT64:
                return value.getInt64();
            case FLOAT:
                return value.getFloat();
            case DOUBLE:
                return value.getDouble();
            case STRING:
                return value.getString();
            case BOOL:
                return value.getBool();
            case OBJECT_MAP:
                return objectMapToJavaMap(value.getObjectMap());
            case LIST_VALUE:
                return value.getListValue().getValueList().stream().map(ProtoRequestConverter::convertProtoValue).toList();
            case VALUE_NOT_SET:
            default:
                log.warn("Unknown or unset value type in ObjectMap.Value");
                return null;
        }
    }

    /**
     * Extracts a string value from the parameters map.
     *
     * @param params the parameters map
     * @param key the key to extract
     * @return the string value, or null if not present or not a string
     */
    private static String extractString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        // Also handle numeric IDs
        if (value instanceof Number) {
            return value.toString();
        }
        return null;
    }

    /**
     * Converts a map to a string-only map for RemoteInferenceInputDataSet.
     *
     * <p>Non-string values are converted to their string representation.
     * Complex types (maps, lists) are serialized to JSON.
     *
     * @param params the source map
     * @return map with string keys and values
     */
    private static Map<String, String> convertToStringMap(Map<String, Object> params) {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            // Convert value to string
            String stringValue;
            if (value instanceof String) {
                stringValue = (String) value;
            } else if (value instanceof Map || value instanceof java.util.List) {
                // For complex types, serialize to proper JSON
                stringValue = toJsonString(value);
            } else {
                stringValue = value.toString();
            }

            result.put(key, stringValue);
        }

        return result;
    }

    /**
     * Serializes an object to JSON string using XContent.
     *
     * @param value the object to serialize (Map or List)
     * @return JSON string representation
     */
    private static String toJsonString(Object value) {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.startObject();
            builder.field("value", value);
            builder.endObject();
            // Extract just the value field content (remove the wrapper)
            String json = builder.toString();
            // Parse out {"value":...} wrapper to get just the value content
            int startIndex = json.indexOf(":{") + 1;
            if (startIndex > 0 && json.endsWith("}}")) {
                return json.substring(startIndex, json.length() - 1);
            } else {
                int arrayStart = json.indexOf(":[");
                if (arrayStart > 0 && json.endsWith("]}")) {
                    return json.substring(arrayStart + 1, json.length() - 1);
                }
            }
            return json;
        } catch (IOException e) {
            log.error("Failed to serialize object to JSON", e);
            return value.toString();
        }
    }
}

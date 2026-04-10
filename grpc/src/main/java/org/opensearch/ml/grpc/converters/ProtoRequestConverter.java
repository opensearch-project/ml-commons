/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.converters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.protobufs.MlExecuteAgentStreamRequest;
import org.opensearch.protobufs.MlPredictModelStreamRequest;
import org.opensearch.protobufs.Parameters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import lombok.extern.log4j.Log4j2;

/**
 * Converts protobuf request messages to ML Commons request objects.
 */
@Log4j2
public class ProtoRequestConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Converts a MlPredictModelStreamRequest protobuf to MLPredictionTaskRequest.
     *
     * @param request the protobuf request
     * @param tenantId the tenant ID extracted from gRPC metadata (nullable)
     * @return MLPredictionTaskRequest for internal processing
     * @throws IllegalArgumentException if required parameters are missing
     */
    public static MLPredictionTaskRequest toPredictRequest(MlPredictModelStreamRequest request, String tenantId) {
        String modelId = request.getModelId();
        if (modelId.isEmpty()) {
            throw new IllegalArgumentException("model_id is required");
        }

        if (!request.hasMlPredictModelStreamRequestBody()) {
            throw new IllegalArgumentException("MlPredictModelStreamRequest must have request body");
        }

        var requestBody = request.getMlPredictModelStreamRequestBody();
        if (!requestBody.hasParameters()) {
            throw new IllegalArgumentException("Request body must have parameters");
        }

        // Convert protobuf message to Map
        Parameters parameters = requestBody.getParameters();
        Map<String, Object> parametersMap = protoMessageToMap(parameters);

        // Convert to string map for RemoteInferenceInputDataSet
        Map<String, String> remoteParams = convertToStringMap(parametersMap);
        remoteParams.put("stream", String.valueOf(true));

        // Create MLInput
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(remoteParams).build();
        MLInput mlInput = RemoteInferenceMLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();
        return new MLPredictionTaskRequest(modelId, mlInput, null, tenantId);
    }

    /**
     * Converts a MlExecuteAgentStreamRequest protobuf to MLExecuteTaskRequest.
     *
     * @param request the protobuf request
     * @param tenantId the tenant ID extracted from gRPC metadata (nullable)
     * @return MLExecuteTaskRequest for internal processing
     * @throws IllegalArgumentException if required parameters are missing
     */
    public static MLExecuteTaskRequest toExecuteRequest(MlExecuteAgentStreamRequest request, String tenantId) {
        String agentId = request.getAgentId();
        if (agentId.isEmpty()) {
            throw new IllegalArgumentException("agent_id is required");
        }

        if (!request.hasMlExecuteAgentStreamRequestBody()) {
            throw new IllegalArgumentException("hasMlExecuteAgentStreamRequestBody must have request body");
        }

        var requestBody = request.getMlExecuteAgentStreamRequestBody();
        if (!requestBody.hasParameters()) {
            throw new IllegalArgumentException("Request body must have parameters");
        }

        // Convert protobuf message to Map
        Parameters parameters = requestBody.getParameters();
        Map<String, Object> parametersMap = protoMessageToMap(parameters);

        // Convert to string map for RemoteInferenceInputDataSet
        Map<String, String> remoteParams = convertToStringMap(parametersMap);
        remoteParams.put("stream", String.valueOf(true));

        // Create input dataset
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(remoteParams).build();

        // Create AgentMLInput with tenant ID
        AgentMLInput agentInput = AgentMLInput
            .AgentMLInputBuilder()
            .agentId(agentId)
            .tenantId(tenantId)
            .functionName(FunctionName.AGENT)
            .inputDataset(inputDataSet)
            .isAsync(false)
            .build();

        return new MLExecuteTaskRequest(FunctionName.AGENT, agentInput);
    }

    /**
     * Converts any Protobuf Message to Java Map using direct field access.
     *
     * @param message the protobuf message to convert
     * @return Map representation of the protobuf message
     */
    private static Map<String, Object> protoMessageToMap(Message message) {
        Map<String, Object> result = new HashMap<>();

        // Iterate through all fields in the protobuf message
        for (Map.Entry<Descriptors.FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
            Descriptors.FieldDescriptor field = entry.getKey();
            Object value = entry.getValue();

            // Convert field name to the appropriate format
            String fieldName = field.getJsonName();

            // Convert field value to appropriate Java type
            Object convertedValue = convertProtoValue(field, value);

            result.put(fieldName, convertedValue);
        }

        return result;
    }

    /**
     * Converts a protobuf field value to the appropriate Java type.
     *
     * @param field the field descriptor
     * @param value the protobuf field value
     * @return converted Java object
     */
    private static Object convertProtoValue(Descriptors.FieldDescriptor field, Object value) {
        if (value == null) {
            return null;
        }

        // Handle repeated fields (lists)
        if (field.isRepeated()) {
            List<?> list = (List<?>) value;
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(convertSingleProtoValue(field, item));
            }
            return result;
        }

        return convertSingleProtoValue(field, value);
    }

    /**
     * Converts a single protobuf value to Java type.
     *
     * @param field the field descriptor
     * @param value the protobuf value
     * @return converted Java object
     */
    private static Object convertSingleProtoValue(Descriptors.FieldDescriptor field, Object value) {
        if (value instanceof Message) {
            // Recursively convert nested messages
            return protoMessageToMap((Message) value);
        }

        // Handle enum values
        if (field.getType() == Descriptors.FieldDescriptor.Type.ENUM) {
            if (value instanceof Descriptors.EnumValueDescriptor) {
                return ((Descriptors.EnumValueDescriptor) value).getName();
            }
        }

        // For primitive types (string, int, bool, etc.), return as-is
        // Protobuf already provides them as appropriate Java types
        return value;
    }

    /**
     * Converts Map<String, Object> to Map<String, String> for RemoteInferenceInputDataSet
     */
    private static Map<String, String> convertToStringMap(Map<String, Object> params) {
        return params
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> convertValueToString(entry.getValue())));
    }

    /**
     * Converts a single value to string
     */
    private static String convertValueToString(Object value) {
        if (value instanceof String str) {
            return str;
        }
        if (value instanceof Map || value instanceof List) {
            return toJsonString(value);
        }
        return value.toString();
    }

    /**
     * Converts an object to JSON string using Jackson
     */
    private static String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert value to JSON string", e);
            throw new IllegalArgumentException("Failed to serialize value to JSON", e);
        }
    }
}

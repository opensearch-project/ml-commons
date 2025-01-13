/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.isJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.action.ActionRequest;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

/**
 * General ModelExecutor interface.
 */
public interface ModelExecutor {

    Configuration suppressExceptionConfiguration = Configuration
        .builder()
        .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
        .build();

    /**
     * Creates an ActionRequest for remote model inference based on the provided parameters and model ID.
     *
     * @param <T>        the type parameter for the ActionRequest
     * @param parameters a map of input parameters for the model inference
     * @param modelId    the ID of the model to be used for inference
     * @return an ActionRequest instance for remote model inference
     * @throws IllegalArgumentException if the input parameters are null
     */
    default <T> ActionRequest getMLModelInferenceRequest(
        NamedXContentRegistry xContentRegistry,
        Map<String, String> parameters,
        Map<String, String> modelConfigs,
        Map<String, String> inputMappings,
        String modelId,
        String functionNameStr,
        String modelInput
    ) throws IOException {
        if (parameters == null) {
            throw new IllegalArgumentException("wrong input. The model input cannot be empty.");
        }
        FunctionName functionName = FunctionName.REMOTE;
        if (functionNameStr != null) {
            functionName = FunctionName.from(functionNameStr);
        }

        Map<String, Object> inputParams = new HashMap<>();
        if (FunctionName.REMOTE == functionName) {
            inputParams.put("parameters", StringUtils.toJson(parameters));
        } else {
            inputParams.putAll(parameters);
        }

        String payload = modelInput;
        StringSubstitutor modelConfigSubstitutor = new StringSubstitutor(modelConfigs, "${model_config.", "}");
        payload = modelConfigSubstitutor.replace(payload);
        StringSubstitutor inputMapSubstitutor = new StringSubstitutor(inputMappings, "${input_map.", "}");
        payload = inputMapSubstitutor.replace(payload);
        StringSubstitutor parametersSubstitutor = new StringSubstitutor(inputParams, "${ml_inference.", "}");
        payload = parametersSubstitutor.replace(payload);

        if (!isJson(payload)) {
            throw new IllegalArgumentException("Invalid payload: " + payload);
        }
        XContentParser parser = XContentType.JSON.xContent().createParser(xContentRegistry, null, payload);

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInput mlInput = MLInput.parse(parser, functionName.name());

        return new MLPredictionTaskRequest(modelId, mlInput);

    }

    /**
     * Retrieves the model output value from the given ModelTensorOutput for the specified modelOutputFieldName.
     * It handles cases where the output contains a single tensor or multiple tensors.
     *
     * @param modelTensorOutput          the ModelTensorOutput containing the model output
     * @param modelOutputFieldName       the name of the field in the model output to retrieve the value for
     * @param ignoreMissing              a flag indicating whether to ignore missing fields or throw an exception
     * @return the model output value as an Object
     * @throws RuntimeException          if there is an error retrieving the model output value
     */
    default Object getModelOutputValue(ModelTensorOutput modelTensorOutput, String modelOutputFieldName, boolean ignoreMissing) {
        Object modelOutputValue;
        try {
            // getMlModelOutputs() returns a list or collection.
            // Adding null check for modelTensorOutput
            if (modelTensorOutput != null
                && modelTensorOutput.getMlModelOutputs() != null
                && !modelTensorOutput.getMlModelOutputs().isEmpty()) {
                // getMlModelOutputs() returns a list of ModelTensors
                // accessing the first element.
                // TODO currently remote model only return single tensor, might need to processor multiple tensors later
                ModelTensors output = modelTensorOutput.getMlModelOutputs().get(0);
                // Adding null check for output
                if (output != null && output.getMlModelTensors() != null && !output.getMlModelTensors().isEmpty()) {
                    // getMlModelTensors() returns a list of ModelTensor
                    if (output.getMlModelTensors().size() == 1) {
                        ModelTensor tensor = output.getMlModelTensors().get(0);
                        // try getDataAsMap first
                        Map<String, ?> tensorInDataAsMap = tensor.getDataAsMap();
                        if (tensorInDataAsMap != null) {
                            modelOutputValue = getModelOutputField(tensorInDataAsMap, modelOutputFieldName, ignoreMissing);
                        }
                        // if dataAsMap is empty try getData
                        else {
                            // parse data type
                            modelOutputValue = parseDataInTensor(tensor);
                        }
                    } else {

                        // for multiple tensors, initiate an array
                        ArrayList tensorArray = new ArrayList<>();
                        for (int i = 0; i < output.getMlModelTensors().size(); i++) {
                            ModelTensor tensor = output.getMlModelTensors().get(i);

                            // Adding null check for tensor
                            if (tensor != null) {
                                // Assuming getData() method may throw an exception
                                // if the data is not available or is in an invalid state.
                                try {
                                    // try getDataAsMap first
                                    Map<String, ?> tensorInDataAsMap = tensor.getDataAsMap();
                                    if (tensorInDataAsMap != null) {
                                        tensorArray.add(getModelOutputField(tensorInDataAsMap, modelOutputFieldName, ignoreMissing));
                                    }
                                    // if dataAsMap is empty try getData
                                    else {
                                        tensorArray.add(parseDataInTensor(tensor));
                                    }
                                } catch (Exception e) {
                                    // Handle the exception accordingly
                                    throw new RuntimeException("Error accessing tensor data: " + e.getMessage());
                                }
                            }
                        }
                        modelOutputValue = tensorArray;
                    }
                } else {
                    throw new RuntimeException("Output tensors are null or empty.");
                }
            } else {
                throw new RuntimeException("Model outputs are null or empty.");
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return modelOutputValue;
    }

    default Object getModelOutputValue(MLOutput mlOutput, String modelOutputFieldName, boolean ignoreMissing, boolean fullResponsePath) {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            String modelOutputJsonStr = mlOutput.toXContent(builder, ToXContent.EMPTY_PARAMS).toString();
            Map<String, Object> modelTensorOutputMap = gson.fromJson(modelOutputJsonStr, Map.class);
            if (!fullResponsePath && mlOutput instanceof ModelTensorOutput) {
                return getModelOutputValue((ModelTensorOutput) mlOutput, modelOutputFieldName, ignoreMissing);
            } else if (modelOutputFieldName == null || modelTensorOutputMap == null) {
                return modelTensorOutputMap;
            } else {
                try {
                    Object modelOutputValue = JsonPath.parse(modelTensorOutputMap).read(modelOutputFieldName);
                    if (modelOutputValue == null) {
                        throw new IllegalArgumentException(
                            "model inference output cannot find such json path: " + modelOutputFieldName + " in " + modelTensorOutputMap
                        );
                    }
                    return modelOutputValue;
                } catch (Exception e) {
                    if (ignoreMissing) {
                        return modelTensorOutputMap;
                    } else {
                        throw new IllegalArgumentException("model inference output cannot find such json path: " + modelOutputFieldName, e);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Parses the data from the given ModelTensor and returns it as an Object.
     * The method handles different data types (integer, floating-point, string, and boolean)
     * and converts the data accordingly.
     *
     * @param tensor the ModelTensor containing the data to be parsed
     * @return the parsed data as an Object (typically a List)
     * @throws RuntimeException if the data type is not supported
     */
    static Object parseDataInTensor(ModelTensor tensor) {
        Object modelOutputValue;
        if (tensor.getDataType().isInteger()) {
            modelOutputValue = Arrays.stream(tensor.getData()).map(Number::intValue).map(Integer::new).collect(Collectors.toList());
        } else if (tensor.getDataType().isFloating()) {
            modelOutputValue = Arrays.stream(tensor.getData()).map(Number::floatValue).map(Float::new).collect(Collectors.toList());
        } else if (tensor.getDataType().isString()) {
            modelOutputValue = Arrays.stream(tensor.getData()).map(String::valueOf).map(String::new).collect(Collectors.toList());
        } else if (tensor.getDataType().isBoolean()) {
            modelOutputValue = Arrays
                .stream(tensor.getData())
                .map(num -> num.intValue() != 0)
                .map(Boolean::new)
                .collect(Collectors.toList());
        } else {
            throw new RuntimeException("unsupported data type in prediction data.");
        }
        return modelOutputValue;
    }

    /**
     * Retrieves the value of the specified field from the given model tensor output map.
     * If the field name is null, it returns the entire map.
     * If the field name is present in the map, it returns the corresponding value.
     * If the field name is not present in the map, it attempts to retrieve the value using JsonPath.
     * If the field is not found and ignoreMissing is true, it returns the entire map.
     * If the field is not found and ignoreMissing is false, it throws an IOException.
     *
     * @param modelTensorOutputMap the model tensor output map to retrieve the field value from
     * @param fieldName            the name of the field to retrieve the value for
     * @param ignoreMissing        a flag indicating whether to ignore missing fields or throw an exception
     * @return the value of the specified field, or the entire map if the field name is null
     * @throws IOException if the field is not found and ignoreMissing is false
     */
    default Object getModelOutputField(Map<String, ?> modelTensorOutputMap, String fieldName, boolean ignoreMissing) throws IOException {
        if (fieldName == null || modelTensorOutputMap == null) {
            return modelTensorOutputMap;
        }
        if (modelTensorOutputMap.containsKey(fieldName)) {
            return modelTensorOutputMap.get(fieldName);
        }
        try {
            return JsonPath.parse(modelTensorOutputMap).read(fieldName);
        } catch (Exception e) {
            if (ignoreMissing) {
                return modelTensorOutputMap;
            } else {
                throw new IllegalArgumentException("model inference output cannot find field name: " + fieldName, e);
            }
        }
    }

    /**
     * Converts the given Object to its JSON string representation using the Gson library.
     *
     * @param originalFieldValue the Object to be converted to JSON string
     * @return the JSON string representation of the input Object
     */

    default String toString(Object originalFieldValue) {
        return StringUtils.toJson(originalFieldValue);
    }

    default boolean hasField(Object json, String path) {
        Object value;
        if (json instanceof String) {
            value = JsonPath.using(suppressExceptionConfiguration).parse((String) json).read(path);
        } else {
            value = JsonPath.using(suppressExceptionConfiguration).parse(json).read(path);
        }
        if (value != null) {
            return true;
        }
        return false;
    }

    /**
     * Writes a new dot path for a nested object within the given JSON object.
     * This method is useful when dealing with arrays or nested objects in the JSON structure.
     * for example foo.*.bar.*.quk to be [foo.0.bar.0.quk, foo.0.bar.1.quk..]
     * @param json    the JSON object containing the nested object
     * @param dotPath the dot path representing the location of the nested object
     * @return a list of dot paths representing the new locations of the nested object
     */
    default List<String> writeNewDotPathForNestedObject(Object json, String dotPath) {
        int lastDotIndex = dotPath.lastIndexOf('.');
        List<String> dotPaths = new ArrayList<>();
        if (lastDotIndex != -1) { // Check if dot exists
            String leadingDotPath = dotPath.substring(0, lastDotIndex);
            String lastLeave = dotPath.substring(lastDotIndex + 1, dotPath.length());
            Configuration configuration = Configuration
                .builder()
                .options(Option.ALWAYS_RETURN_LIST, Option.AS_PATH_LIST, Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build();

            List<String> resultPaths = JsonPath.using(configuration).parse(json).read(leadingDotPath);
            for (String path : resultPaths) {
                dotPaths.add(convertToDotPath(path) + "." + lastLeave);
            }
            return dotPaths;
        } else {
            dotPaths.add(dotPath);
        }
        return dotPaths;
    }

    /**
     * Converts a JSONPath format string to a dot path notation format.
     * For example, it converts "$['foo'][0]['bar']['quz'][0]" to "foo.0.bar.quiz.0".
     *
     * @param path the JSONPath format string to be converted
     * @return the converted dot path notation string
     */
    default String convertToDotPath(String path) {
        return path.replaceAll("\\[(\\d+)\\]", "$1\\.").replaceAll("\\['(.*?)']", "$1\\.").replaceAll("^\\$", "").replaceAll("\\.$", "");
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.opensearch.action.ValidateActions.addValidationError;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensearch.OpenSearchParseException;
import org.opensearch.action.ActionRequestValidationException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class StringUtils {

    public static final String DEFAULT_ESCAPE_FUNCTION = "\n    String escape(def input) { \n"
        + "      if (input.contains(\"\\\\\")) {\n        input = input.replace(\"\\\\\", \"\\\\\\\\\");\n      }\n"
        + "      if (input.contains(\"\\\"\")) {\n        input = input.replace(\"\\\"\", \"\\\\\\\"\");\n      }\n"
        + "      if (input.contains('\r')) {\n        input = input = input.replace('\r', '\\\\r');\n      }\n"
        + "      if (input.contains(\"\\\\t\")) {\n        input = input.replace(\"\\\\t\", \"\\\\\\\\\\\\t\");\n      }\n"
        + "      if (input.contains('\n')) {\n        input = input.replace('\n', '\\\\n');\n      }\n"
        + "      if (input.contains('\b')) {\n        input = input.replace('\b', '\\\\b');\n      }\n"
        + "      if (input.contains('\f')) {\n        input = input.replace('\f', '\\\\f');\n      }\n"
        + "      return input;"
        + "\n    }\n";

    // Regex allows letters, digits, spaces, hyphens, underscores, and dots.
    private static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s.,!?():@\\-_/'\"]*$");

    public static final String SAFE_INPUT_DESCRIPTION = "can only contain letters, numbers, spaces, and basic punctuation (.,!?():@-_'/\")";

    public static final Gson gson;

    static {
        gson = new Gson();
    }
    public static final String TO_STRING_FUNCTION_NAME = ".toString()";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static boolean isValidJsonString(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }

        try {
            new JSONObject(json);
        } catch (JSONException ex) {
            try {
                new JSONArray(json);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

    public static boolean isJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }

        try {
            if (!isValidJsonString(json)) {
                return false;
            }
            // This is to cover such edge case "[]\""
            gson.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException ex) {
            return false;
        }
    }

    /**
     * Ensures that a string is properly JSON escaped.
     *
     * <p>This method examines the input string and determines whether it already represents
     * valid JSON content. If the input is valid JSON, it is returned unchanged. Otherwise,
     * the input is treated as a plain string and escaped according to JSON string literal
     * rules.</p>
     *
     * <p>Examples:</p>
     * <pre>
     *   prepareJsonValue("hello")        → "\"hello\""
     *   prepareJsonValue("\"hello\"")        → "\\\"hello\\\""
     *   prepareJsonValue("{\"key\":123}") → {\"key\":123} (valid JSON object, unchanged)
     * </pre>
     * @param input
     * @return
     */
    public static String prepareJsonValue(String input) {
        if (isJson(input)) {
            return input;
        }
        return escapeJson(input);
    }

    public static String toUTF8(String rawString) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(rawString);

        String utf8EncodedString = StandardCharsets.UTF_8.decode(buffer).toString();
        return utf8EncodedString;
    }

    public static Map<String, Object> fromJson(String jsonStr, String defaultKey) {
        Map<String, Object> result;
        JsonElement jsonElement = JsonParser.parseString(jsonStr);
        if (jsonElement.isJsonObject()) {
            result = gson.fromJson(jsonElement, Map.class);
        } else if (jsonElement.isJsonArray()) {
            List<Object> list = gson.fromJson(jsonElement, List.class);
            result = new HashMap<>();
            result.put(defaultKey, list);
        } else {
            throw new IllegalArgumentException("Unsupported response type");
        }
        return result;
    }

    public static Map<String, String> filteredParameterMap(Map<String, ?> parameterObjs, Set<String> allowedList) {
        Map<String, String> parameters = new HashMap<>();
        Set<String> filteredKeys = new HashSet<>(parameterObjs.keySet());
        filteredKeys.retainAll(allowedList);
        for (String key : filteredKeys) {
            Object value = parameterObjs.get(key);
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    if (value instanceof String) {
                        parameters.put(key, (String) value);
                    } else {
                        parameters.put(key, gson.toJson(value));
                    }
                    return null;
                });
            } catch (PrivilegedActionException e) {
                throw new RuntimeException(e);
            }
        }
        return parameters;
    }

    @SuppressWarnings("removal")
    public static Map<String, String> getParameterMap(Map<String, ?> parameterObjs) {
        Map<String, String> parameters = new HashMap<>();
        for (String key : parameterObjs.keySet()) {
            Object value = parameterObjs.get(key);
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    if (value instanceof String) {
                        parameters.put(key, (String) value);
                    } else {
                        parameters.put(key, gson.toJson(value));
                    }
                    return null;
                });
            } catch (PrivilegedActionException e) {
                throw new RuntimeException(e);
            }
        }
        return parameters;
    }

    @SuppressWarnings("removal")
    public static String toJson(Object value) {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
                if (value instanceof String) {
                    return (String) value;
                } else {
                    return gson.toJson(value);
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("removal")
    public static Map<String, String> convertScriptStringToJsonString(Map<String, Object> processedInput) {
        Map<String, String> parameterStringMap = new HashMap<>();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                Map<String, Object> parametersMap = (Map<String, Object>) processedInput.getOrDefault("parameters", Map.of());
                for (String key : parametersMap.keySet()) {
                    if (parametersMap.get(key) instanceof String) {
                        parameterStringMap.put(key, (String) parametersMap.get(key));
                    } else {
                        parameterStringMap.put(key, gson.toJson(parametersMap.get(key)));
                    }
                }
                return null;
            });
        } catch (PrivilegedActionException e) {
            log.error("Error processing parameters", e);
            throw new RuntimeException(e);
        }
        return parameterStringMap;
    }

    public static List<String> processTextDocs(List<String> inputDocs) {
        List<String> docs = new ArrayList<>();
        for (String doc : inputDocs) {
            docs.add(processTextDoc(doc));
        }
        return docs;
    }

    public static String processTextDoc(String doc) {
        if (doc != null) {
            String gsonString = gson.toJson(doc);
            // in 2.9, user will add " before and after string
            // gson.toString(string) will add extra " before after string, so need to remove
            return gsonString.substring(1, gsonString.length() - 1);
        } else {
            return null;
        }
    }

    public static String addDefaultMethod(String functionScript) {
        if (!containsEscapeMethod(functionScript) && isEscapeUsed(functionScript)) {
            return DEFAULT_ESCAPE_FUNCTION + functionScript;
        }
        return functionScript;
    }

    public static boolean patternExist(String input, String patternString) {
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }

    public static boolean isEscapeUsed(String input) {
        return patternExist(input, "(?<!\\bString\\s+)\\bescape\\s*\\(");
    }

    public static boolean containsEscapeMethod(String input) {
        return patternExist(input, "String\\s+escape\\s*\\(\\s*(def|String)\\s+.*?\\)\\s*\\{?");
    }

    /**
     * This method will define if we should print out model id with the error message or not.
     * @param errorMessage
     * @param modelId
     * @param isHidden
     * @return
     */
    public static String getErrorMessage(String errorMessage, String modelId, Boolean isHidden) {
        if (BooleanUtils.isTrue(isHidden)) {
            return errorMessage;
        } else {
            return errorMessage + " Model ID: " + modelId;
        }
    }

    /**
     * Collects the prefixes of the toString() method calls present in the values of the given map.
     *
     * @param map A map containing key-value pairs where the values may contain toString() method calls.
     * @return A list of prefixes for the toString() method calls found in the map values.
     */
    public static List<String> collectToStringPrefixes(Map<String, String> map) {
        List<String> prefixes = new ArrayList<>();
        for (String key : map.keySet()) {
            String value = map.get(key);
            if (value != null) {
                Pattern pattern = Pattern.compile("\\$\\{parameters\\.(.+?)\\.toString\\(\\)\\}");
                Matcher matcher = pattern.matcher(value);
                while (matcher.find()) {
                    String prefix = matcher.group(1);
                    prefixes.add(prefix);
                }
            }
        }
        return prefixes;
    }

    /**
     * Parses the given parameters map and processes the values containing toString() method calls.
     *
     * @param parameters A map containing key-value pairs where the values may contain toString() method calls.
     * @return A new map with the processed values for the toString() method calls.
     */
    public static Map<String, String> parseParameters(Map<String, String> parameters) {
        if (parameters != null) {
            List<String> toStringParametersPrefixes = collectToStringPrefixes(parameters);

            if (!toStringParametersPrefixes.isEmpty()) {
                for (String prefix : toStringParametersPrefixes) {
                    String value = parameters.get(prefix);
                    if (value != null) {
                        parameters.put(prefix + TO_STRING_FUNCTION_NAME, processTextDoc(value));
                    }
                }
            }
        }
        return parameters;
    }

    public static String obtainFieldNameFromJsonPath(String jsonPath) {
        String[] parts = jsonPath.split("\\.");

        // Get the last part which is the field name
        return parts[parts.length - 1];
    }

    public static String getJsonPath(String jsonPathWithSource) {
        // Find the index of the first occurrence of "$."
        int startIndex = jsonPathWithSource.indexOf("$.");

        // Extract the substring from the startIndex to the end of the input string
        return (startIndex != -1) ? jsonPathWithSource.substring(startIndex) : jsonPathWithSource;
    }

    /**
     * Checks if the given input string matches the JSONPath format.
     *
     * <p>The JSONPath format is a way to navigate and extract data from JSON documents.
     * It uses a syntax similar to XPath for XML documents. This method attempts to compile
     * the input string as a JSONPath expression using the {@link com.jayway.jsonpath.JsonPath}
     * library. If the compilation succeeds, it means the input string is a valid JSONPath
     * expression.
     *
     * @param input the input string to be checked for JSONPath format validity
     * @return true if the input string is a valid JSONPath expression, false otherwise
     */
    public static boolean isValidJSONPath(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        try {
            JsonPath.compile(input); // This will throw an exception if the path is invalid
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonObject getJsonObjectFromString(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            throw new IllegalArgumentException("Json cannot be null or empty");
        }

        return JsonParser.parseString(jsonString).getAsJsonObject();
    }

    /**
     * Checks if a specified JSON path exists within a given JSON object.
     *
     * This method attempts to read the value at the specified path in the JSON object.
     * If the path exists, it returns true. If a PathNotFoundException is thrown,
     * indicating that the path does not exist, it returns false.
     *
     * @param json The JSON object to check. This can be a Map, List, or any object
     *             that JsonPath can parse.
     * @param path The JSON path to check for existence. This should be a valid
     *             JsonPath expression (e.g., "$.store.book[0].title").
     * @return true if the path exists in the JSON object, false otherwise.
     * @throws IllegalArgumentException if the json object is null or if the path is null or empty.
     */
    public static boolean pathExists(Object json, String path) {
        if (json == null) {
            throw new IllegalArgumentException("JSON object cannot be null");
        }
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (!isValidJSONPath(path)) {
            throw new IllegalArgumentException("the field path is not a valid json path: " + path);
        }
        try {
            JsonPath.read(json, path);
            return true;
        } catch (PathNotFoundException e) {
            return false;
        }
    }

    /**
     * Prepares nested structures in a JSON object based on the given field path.
     *
     * This method ensures that all intermediate nested objects and arrays exist in the JSON object
     * for a given field path. If any part of the path doesn't exist, it creates new empty objects
     * (HashMaps) or arrays (ArrayLists) for those parts.
     *
     * The method can handle complex paths including both object properties and array indices.
     * For example, it can process paths like "foo.bar[1].baz[0].qux".
     *
     * @param jsonObject The JSON object to be updated. If this is not a Map, a new Map will be created.
     * @param fieldPath The full path of the field, potentially including nested structures and array indices.
     *                  The path can optionally start with "$." which will be ignored if present.
     * @return The updated JSON object with necessary nested structures in place.
     *         If the input was not a Map, returns the newly created Map structure.
     *
     * @throws IllegalArgumentException If the field path is null or not a valid JSON path.
     *
     */
    public static Object prepareNestedStructures(Object jsonObject, String fieldPath) {
        if (fieldPath == null) {
            throw new IllegalArgumentException("The field path is null");
        }
        if (jsonObject == null) {
            throw new IllegalArgumentException("The object is null");
        }
        if (!isValidJSONPath(fieldPath)) {
            throw new IllegalArgumentException("The field path is not a valid JSON path: " + fieldPath);
        }

        String path = fieldPath.startsWith("$.") ? fieldPath.substring(2) : fieldPath;
        String[] pathParts = path.split("(?<!\\\\)\\.");

        Map<String, Object> current = (jsonObject instanceof Map) ? (Map<String, Object>) jsonObject : new HashMap<>();

        for (String part : pathParts) {
            if (part.contains("[")) {
                // Handle array notation
                String[] arrayParts = part.split("\\[");
                String key = arrayParts[0];
                int index = Integer.parseInt(arrayParts[1].replaceAll("\\]", ""));

                if (!current.containsKey(key)) {
                    current.put(key, new ArrayList<>());
                }
                if (!(current.get(key) instanceof List)) {
                    return jsonObject;
                }
                List<Object> list = (List<Object>) current.get(key);
                if (index >= list.size()) {
                    while (list.size() <= index) {
                        list.add(null);
                    }
                    list.set(index, new HashMap<>());
                }
                if (!(list.get(index) instanceof Map)) {
                    return jsonObject;
                }
                current = (Map<String, Object>) list.get(index);
            } else {
                // Handle object notation
                if (!current.containsKey(part)) {
                    current.put(part, new HashMap<>());
                } else if (!(current.get(part) instanceof Map)) {
                    return jsonObject;
                }
                current = (Map<String, Object>) current.get(part);
            }
        }

        return jsonObject;
    }

    public static void validateSchema(String schemaString, String instanceString) {
        try {
            // parse the schema JSON as string
            JsonNode schemaNode = MAPPER.readTree(schemaString);
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);

            // JSON data to validate
            JsonNode jsonNode = MAPPER.readTree(instanceString);

            // Validate JSON node against the schema
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) {
                String errorMessage = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining(", "));

                throw new OpenSearchParseException(
                    "Validation failed: " + errorMessage + " for instance: " + instanceString + " with schema: " + schemaString
                );
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new OpenSearchParseException("Schema validation failed: " + e.getMessage(), e);
        }
    }

    public static String hashString(String input) {
        try {
            // Create a MessageDigest instance for SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Perform the hashing and get the byte array
            byte[] hashBytes = digest.digest(input.getBytes());

            // Convert the byte array to a Base64 encoded string
            return Base64.getUrlEncoder().encodeToString(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error: Unable to compute hash", e);
        }
    }

    /**
     * Validates a map of fields to ensure that their values only contain allowed characters.
     * <p>
     * Allowed characters are: letters, digits, spaces, underscores (_), hyphens (-), dots (.), and colons (:).
     * If a value does not comply, a validation error is added.
     *
     * @param fields A map where the key is the field name (used for error messages) and the value is the text to validate.
     * @return An {@link ActionRequestValidationException} containing all validation errors, or {@code null} if all fields are valid.
     */
    public static ActionRequestValidationException validateFields(Map<String, FieldDescriptor> fields) {
        ActionRequestValidationException exception = null;

        for (Map.Entry<String, FieldDescriptor> entry : fields.entrySet()) {
            String key = entry.getKey();
            FieldDescriptor descriptor = entry.getValue();
            String value = descriptor.getValue();

            if (descriptor.isRequired()) {
                if (!isSafeText(value)) {
                    String reason = (value == null || value.isBlank()) ? "is required and cannot be null or blank" : SAFE_INPUT_DESCRIPTION;
                    exception = addValidationError(key + " " + reason, exception);
                }
            } else {
                if (value != null && !value.isBlank() && !matchesSafePattern(value)) {
                    exception = addValidationError(key + " " + SAFE_INPUT_DESCRIPTION, exception);
                }
            }
        }

        return exception;
    }

    /**
     * Checks if the input is safe (non-null, non-blank, matches safe character set).
     *
     * @param value The input string to validate
     * @return true if input is safe, false otherwise
     */
    public static boolean isSafeText(String value) {
        return value != null && !value.isBlank() && matchesSafePattern(value);
    }

    // Just checks pattern
    public static boolean matchesSafePattern(String value) {
        return SAFE_INPUT_PATTERN.matcher(value).matches();
    }

    /**
     * Parses a JSON array string into a List of Strings.
     *
     * @param jsonArrayString JSON array string to parse (e.g., "[\"item1\", \"item2\"]")
     * @return List of strings parsed from the JSON array, or an empty list if the input is
     *         null, empty, or invalid JSON
     */
    public static List<String> parseStringArrayToList(String jsonArrayString) {
        if (jsonArrayString == null || jsonArrayString.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return gson.fromJson(jsonArrayString, TypeToken.getParameterized(List.class, String.class).getType());
        } catch (JsonSyntaxException e) {
            log.error("Failed to parse JSON array string: {}", jsonArrayString, e);
            return Collections.emptyList();
        }
    }
}

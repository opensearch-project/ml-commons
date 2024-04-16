/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class StringUtils {

    public static final String DEFAULT_ESCAPE_FUNCTION = "\n    String escape(def input) { \n" +
            "      if (input.contains(\"\\\\\")) {\n        input = input.replace(\"\\\\\", \"\\\\\\\\\");\n      }\n" +
            "      if (input.contains(\"\\\"\")) {\n        input = input.replace(\"\\\"\", \"\\\\\\\"\");\n      }\n" +
            "      if (input.contains('\r')) {\n        input = input = input.replace('\r', '\\\\r');\n      }\n" +
            "      if (input.contains(\"\\\\t\")) {\n        input = input.replace(\"\\\\t\", \"\\\\\\\\\\\\t\");\n      }\n" +
            "      if (input.contains('\n')) {\n        input = input.replace('\n', '\\\\n');\n      }\n" +
            "      if (input.contains('\b')) {\n        input = input.replace('\b', '\\\\b');\n      }\n" +
            "      if (input.contains('\f')) {\n        input = input.replace('\f', '\\\\f');\n      }\n" +
            "      return input;" +
            "\n    }\n";

    public static final Gson gson;

    static {
        gson = new Gson();
    }

    public static boolean isValidJsonString(String Json) {
        try {
            new JSONObject(Json);
        } catch (JSONException ex) {
            try {
                new JSONArray(Json);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

    public static boolean isJson(String json) {
        try {
            if (!isValidJsonString(json)) {
                return false;
            }
            //This is to cover such edge case "[]\""
            gson.fromJson(json, Object.class);
            return true;
        } catch(JsonSyntaxException ex) {
            return false;
        }
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

    @SuppressWarnings("removal")
    public static Map<String, String> getParameterMap(Map<String, ?> parameterObjs) {
        Map<String, String> parameters = new HashMap<>();
        for (String key : parameterObjs.keySet()) {
            Object value = parameterObjs.get(key);
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    if (value instanceof String) {
                        parameters.put(key, (String)value);
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
                Map<String, Object> parametersMap = (Map<String, Object>) processedInput.get("parameters");
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
        return patternExist(input,"(?<!\\bString\\s+)\\bescape\\s*\\(");
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
    public static String getErrorMessage(String errorMessage, String modelId, boolean isHidden) {
        if (isHidden) {
            return errorMessage;
        } else {
            return errorMessage + " Model ID: " + modelId;
        }
    }
}

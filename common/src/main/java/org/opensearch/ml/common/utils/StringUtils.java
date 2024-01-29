/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class StringUtils {

    public static final Gson gson;

    static {
        gson = new Gson();
    }

    public static boolean isJson(String Json) {
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
}

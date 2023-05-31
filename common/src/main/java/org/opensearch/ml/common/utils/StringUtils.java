/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}

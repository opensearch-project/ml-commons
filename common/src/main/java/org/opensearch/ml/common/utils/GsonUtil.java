package org.opensearch.ml.common.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;

public class GsonUtil {

    private static final Gson gson;

    static {
        gson = new Gson();
    }

    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }

    public static <T> T fromJson(JsonElement jsonElement, Class<T> clazz) {
        return gson.fromJson(jsonElement, clazz);
    }

    public static <T> T fromJson(JsonReader jsonReader, Class<T> clazz) {
        return gson.fromJson(jsonReader, clazz);
    }
}

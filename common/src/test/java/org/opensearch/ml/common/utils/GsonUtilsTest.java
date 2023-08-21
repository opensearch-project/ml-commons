package org.opensearch.ml.common.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class GsonUtilsTest {
    @Test
    public void test_toJson() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        String mapString = GsonUtil.toJson(map);
        assert mapString.equals("{\"key\":\"value\"}");
    }

    @Test
    public void test_fromJsonString() {
        Map<String, String> map = GsonUtil.fromJson("{\"key\": \"value\"}", Map.class);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("value", map.get("key"));
    }

    @Test
    public void test_fromJsonJsonElement() {
        JsonElement jsonElement = JsonParser.parseString("{\"key\": \"value\"}");
        Map<String, String> map = GsonUtil.fromJson(jsonElement, Map.class);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("value", map.get("key"));
    }

    @Test
    public void test_fromJsonJsonReader() {
        JsonReader reader = new JsonReader(new StringReader("{\"key\": \"value\"}"));
        Map<String, String> map = GsonUtil.fromJson(reader, Map.class);
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("value", map.get("key"));
    }



}

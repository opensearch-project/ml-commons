package org.opensearch.ml.common.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class StringUtilsTest {

    @Test
    public void isJson_True() {
        Assert.assertTrue(StringUtils.isJson("{}"));
        Assert.assertTrue(StringUtils.isJson("[]"));
        Assert.assertTrue(StringUtils.isJson("{\"key\": \"value\"}"));
        Assert.assertTrue(StringUtils.isJson("{\"key\": 123}"));
        Assert.assertTrue(StringUtils.isJson("[1, 2, 3]"));
        Assert.assertTrue(StringUtils.isJson("[\"a\", \"b\"]"));
        Assert.assertTrue(StringUtils.isJson("[1, \"a\"]"));
    }

    @Test
    public void isJson_False() {
        Assert.assertFalse(StringUtils.isJson("{"));
        Assert.assertFalse(StringUtils.isJson("["));
        Assert.assertFalse(StringUtils.isJson("{\"key\": \"value}"));
        Assert.assertFalse(StringUtils.isJson("{\"key\": \"value\", \"key\": 123}"));
        Assert.assertFalse(StringUtils.isJson("[1, \"a]"));
    }

    @Test
    public void toUTF8() {
        String rawString = "\uD83D\uDE00\uD83D\uDE0D\uD83D\uDE1C";
        String utf8 = StringUtils.toUTF8(rawString);
        Assert.assertNotNull(utf8);
    }

    @Test
    public void fromJson_SimpleMap() {
        Map<String, Object> response = StringUtils.fromJson("{\"key\": \"value\"}", "response");
        Assert.assertEquals(1, response.size());
        Assert.assertEquals("value", response.get("key"));
    }

    @Test
    public void fromJson_NestedMap() {
        Map<String, Object> response = StringUtils
            .fromJson("{\"key\": {\"nested_key\": \"nested_value\", \"nested_array\": [1, \"a\"]}}", "response");
        Assert.assertEquals(1, response.size());
        Assert.assertTrue(response.get("key") instanceof Map);
        Map nestedMap = (Map) response.get("key");
        Assert.assertEquals("nested_value", nestedMap.get("nested_key"));
        List list = (List) nestedMap.get("nested_array");
        Assert.assertEquals(2, list.size());
        Assert.assertEquals(1.0, list.get(0));
        Assert.assertEquals("a", list.get(1));
    }

    @Test
    public void fromJson_SimpleList() {
        Map<String, Object> response = StringUtils.fromJson("[1, \"a\"]", "response");
        Assert.assertEquals(1, response.size());
        Assert.assertTrue(response.get("response") instanceof List);
        List list = (List) response.get("response");
        Assert.assertEquals(1.0, list.get(0));
        Assert.assertEquals("a", list.get(1));
    }

    @Test
    public void fromJson_NestedList() {
        Map<String, Object> response = StringUtils.fromJson("[1, \"a\", [2, 3], {\"key\": \"value\"}]", "response");
        Assert.assertEquals(1, response.size());
        Assert.assertTrue(response.get("response") instanceof List);
        List list = (List) response.get("response");
        Assert.assertEquals(1.0, list.get(0));
        Assert.assertEquals("a", list.get(1));
        Assert.assertTrue(list.get(2) instanceof List);
        Assert.assertTrue(list.get(3) instanceof Map);
    }

    @Test
    public void getParameterMap() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        parameters.put("key2", 2);
        parameters.put("key3", 2.1);
        parameters.put("key4", new int[] { 10, 20 });
        parameters.put("key5", new Object[] { 1.01, "abc" });
        Map<String, String> parameterMap = StringUtils.getParameterMap(parameters);
        System.out.println(parameterMap);
        Assert.assertEquals(5, parameterMap.size());
        Assert.assertEquals("value1", parameterMap.get("key1"));
        Assert.assertEquals("2", parameterMap.get("key2"));
        Assert.assertEquals("2.1", parameterMap.get("key3"));
        Assert.assertEquals("[10,20]", parameterMap.get("key4"));
        Assert.assertEquals("[1.01,\"abc\"]", parameterMap.get("key5"));
    }
}

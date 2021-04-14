package org.opensearch.ml.common.parameter;

import org.junit.Test;

import static org.junit.Assert.*;

public class MLParameterBuilderTest {

    @Test
    public void parameter_IntValue() {
        MLParameter parameter = MLParameterBuilder.parameter("key", 1);
        assertEquals(1, parameter.getValue());
        assertEquals("key", parameter.getName());
    }

    @Test
    public void parameter_IntArrayValue() {
        MLParameter parameter = MLParameterBuilder.parameter("key", new int[]{1});
        assertEquals(1, ((int[])parameter.getValue())[0]);
        assertEquals("key", parameter.getName());
    }

    @Test
    public void parameter_DoubleValue() {
        MLParameter parameter = MLParameterBuilder.parameter("key", 2.1D);
        assertEquals(2.1D, (double)parameter.getValue(), 0.0001D);
        assertEquals("key", parameter.getName());
    }

    @Test
    public void parameter_DoubleArrayValue() {
        MLParameter parameter = MLParameterBuilder.parameter("key", new double[]{2.1D, 2.2D});
        assertEquals(2, ((double[])parameter.getValue()).length);
        assertEquals("key", parameter.getName());
    }

    @Test
    public void parameter_StringValue() {
        MLParameter parameter = MLParameterBuilder.parameter("key", "string");
        assertEquals("string", parameter.getValue());
        assertEquals("key", parameter.getName());
    }

    @Test
    public void parameter_StringArrayValue() {
        MLParameter parameter = MLParameterBuilder.parameter("key", new String[]{"str1", "str2"});
        assertEquals(2, ((String[])parameter.getValue()).length);
        assertEquals("key", parameter.getName());
    }

    @Test
    public void parameter_BooleanValue() {
        MLParameter parameter = MLParameterBuilder.parameter("key", true);
        assertEquals(true, parameter.getValue());
        assertEquals("key", parameter.getName());
    }

    @Test
    public void parameter_BooleanArrayValue() {
        MLParameter parameter = MLParameterBuilder.parameter("key", new boolean[]{true, false});
        assertEquals(2, ((boolean[])parameter.getValue()).length);
        assertEquals("key", parameter.getName());
    }
}
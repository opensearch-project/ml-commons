/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

import com.google.gson.stream.JsonWriter;

public class ToStringTypeAdapterTest {

    private ToStringTypeAdapter<ModelTensor> adapter;
    private ModelTensor modelTensor;

    @Before
    public void setUp() {
        adapter = new ToStringTypeAdapter<>(ModelTensor.class);
        modelTensor = ModelTensor.builder().name("test_tensor").data(new Number[] { 1, 2, 3 }).dataType(MLResultDataType.INT32).build();
    }

    @Test
    public void test_Write_ValidObject() throws IOException {
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);

        adapter.write(jsonWriter, modelTensor);

        String result = stringWriter.toString();
        assertEquals(modelTensor.toString(), result);
    }

    @Test
    public void test_Write_NullObject() throws IOException {
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);

        adapter.write(jsonWriter, null);

        String result = stringWriter.toString();
        assertEquals("null", result);
    }

    @Test
    public void test_Read_ThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> { adapter.read(null); });
    }
}

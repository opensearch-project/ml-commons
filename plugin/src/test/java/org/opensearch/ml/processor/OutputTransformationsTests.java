/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import java.util.Arrays;
import java.util.List;

import org.opensearch.test.OpenSearchTestCase;

public class OutputTransformationsTests extends OpenSearchTestCase {

    public void testHasTransformation_WithMeanPooling() {
        assertTrue(OutputTransformations.hasTransformation("field.meanPooling()"));
        assertTrue(OutputTransformations.hasTransformation("nested.field.meanPooling()"));
    }

    public void testHasTransformation_WithoutTransformation() {
        assertFalse(OutputTransformations.hasTransformation("field"));
        assertFalse(OutputTransformations.hasTransformation("nested.field"));
        assertFalse(OutputTransformations.hasTransformation(null));
    }

    public void testGetBaseFieldName_WithMeanPooling() {
        assertEquals("field", OutputTransformations.getBaseFieldName("field.meanPooling()"));
        assertEquals("nested.field", OutputTransformations.getBaseFieldName("nested.field.meanPooling()"));
    }

    public void testGetBaseFieldName_WithoutTransformation() {
        assertEquals("field", OutputTransformations.getBaseFieldName("field"));
        assertEquals("nested.field", OutputTransformations.getBaseFieldName("nested.field"));
    }

    public void testApplyMeanPooling_Success() {
        List<List<Double>> input = Arrays.asList(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(4.0, 5.0, 6.0), Arrays.asList(7.0, 8.0, 9.0));

        List<Float> result = (List<Float>) OutputTransformations.applyMeanPooling(input);

        assertEquals(3, result.size());
        assertEquals(4.0, result.get(0), 0.001); // (1+4+7)/3
        assertEquals(5.0, result.get(1), 0.001); // (2+5+8)/3
        assertEquals(6.0, result.get(2), 0.001); // (3+6+9)/3
    }

    public void testApplyMeanPooling_SingleVector() {
        List<List<Double>> input = Arrays.asList(Arrays.asList(1.0, 2.0, 3.0));

        List<Float> result = (List<Float>) OutputTransformations.applyMeanPooling(input);

        assertEquals(3, result.size());
        assertEquals(1.0, result.get(0), 0.001);
        assertEquals(2.0, result.get(1), 0.001);
        assertEquals(3.0, result.get(2), 0.001);
    }

    public void testApplyMeanPooling_InvalidInput_NotList() {
        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> OutputTransformations.applyMeanPooling("not a list")
        );
        assertEquals("Mean pooling requires a list input", exception.getMessage());
    }

    public void testApplyMeanPooling_InvalidInput_NotNestedList() {
        List<String> input = Arrays.asList("not", "nested", "list");

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> OutputTransformations.applyMeanPooling(input)
        );
        assertEquals("Mean pooling requires nested array structure", exception.getMessage());
    }

    public void testApplyMeanPooling_InvalidInput_DifferentDimensions() {
        List<List<Double>> input = Arrays
            .asList(
                Arrays.asList(1.0, 2.0, 3.0),
                Arrays.asList(4.0, 5.0) // Different dimension
            );

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> OutputTransformations.applyMeanPooling(input)
        );
        assertEquals("All vectors must have the same dimension", exception.getMessage());
    }
}

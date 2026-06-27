/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MLModelFormatTests {
    @Test
    public void from() {
        MLModelFormat modelFormat = MLModelFormat.from("TORCH_SCRIPT");
        assertEquals(MLModelFormat.TORCH_SCRIPT, modelFormat);
    }

    @Test
    public void from_wrongValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLModelFormat.from("test_wrong_value"));
        assertEquals("Wrong model format", exception.getMessage());
    }
}

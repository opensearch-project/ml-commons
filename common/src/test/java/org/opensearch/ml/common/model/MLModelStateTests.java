/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MLModelStateTests {
    @Test
    public void from() {
        MLModelState modelState = MLModelState.from("REGISTERED");
        assertEquals(MLModelState.REGISTERED, modelState);
    }

    @Test
    public void from_wrongValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLModelState.from("test_wrong_value"));
        assertEquals("Wrong model state", exception.getMessage());
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MLModelFormatTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void from() {
        MLModelFormat modelFormat = MLModelFormat.from("TORCH_SCRIPT");
        assertEquals(MLModelFormat.TORCH_SCRIPT, modelFormat);
    }

    @Test
    public void from_wrongValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong model format");
        MLModelFormat.from("test_wrong_value");
    }
}

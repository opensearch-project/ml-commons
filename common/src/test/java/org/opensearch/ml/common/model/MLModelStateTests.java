/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MLModelStateTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void from() {
        MLModelState modelState = MLModelState.from("REGISTERED");
        assertEquals(MLModelState.REGISTERED, modelState);
    }

    @Test
    public void from_wrongValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong model state");
        MLModelState.from("test_wrong_value");
    }

}

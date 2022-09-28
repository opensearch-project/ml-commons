/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

public class MLModelTaskTypeTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void from() {
        MLModelTaskType taskType = MLModelTaskType.from("TEXT_EMBEDDING");
        assertEquals(MLModelTaskType.TEXT_EMBEDDING, taskType);
    }

    @Test
    public void from_wrongValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong model task type");
        MLModelTaskType.from("test_wrong_value");
    }
}

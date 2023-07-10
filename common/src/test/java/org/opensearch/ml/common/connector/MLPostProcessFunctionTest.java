/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.junit.Assert;
import org.junit.Test;

import static org.opensearch.ml.common.connector.MLPostProcessFunction.OPENAI_EMBEDDING;

public class MLPostProcessFunctionTest {

    @Test
    public void contains() {
        Assert.assertTrue(MLPostProcessFunction.contains(OPENAI_EMBEDDING));
        Assert.assertFalse(MLPostProcessFunction.contains("wrong value"));
    }

    @Test
    public void get() {
        Assert.assertNotNull(MLPostProcessFunction.get(OPENAI_EMBEDDING));
        Assert.assertNull(MLPostProcessFunction.get("wrong value"));
    }
}

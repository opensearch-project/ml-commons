/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;

import org.junit.Assert;
import org.junit.Test;

public class MLPreProcessFunctionTest {

    @Test
    public void contains() {
        Assert.assertTrue(MLPreProcessFunction.contains(TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT));
        Assert.assertFalse(MLPreProcessFunction.contains("wrong value"));
    }

    @Test
    public void get() {
        Assert.assertNotNull(MLPreProcessFunction.get(TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT));
        Assert.assertNull(MLPreProcessFunction.get("wrong value"));
    }
}

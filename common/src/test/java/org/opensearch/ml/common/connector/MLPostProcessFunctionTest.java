/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.MLPostProcessFunction.OPENAI_EMBEDDING;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MLPostProcessFunctionTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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

    @Test
    public void test_getResponseFilter() {
        assert null != MLPostProcessFunction.getResponseFilter(OPENAI_EMBEDDING);
        assert null == MLPostProcessFunction.getResponseFilter("wrong value");
    }

    @Test
    public void test_buildModelTensorList() {
        Assert.assertNotNull(MLPostProcessFunction.buildModelTensorList());
        List<List<Float>> numbersList = new ArrayList<>();
        numbersList.add(Collections.singletonList(1.0f));
        Assert.assertNotNull(MLPostProcessFunction.buildModelTensorList().apply(numbersList));
    }

    @Test
    public void test_buildModelTensorList_exception() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLPostProcessFunction.buildModelTensorList().apply(null);
    }
}

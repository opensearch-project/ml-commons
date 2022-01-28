/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.SampleAlgoParams;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MLCommonsClassLoaderTests {

    private SampleAlgoParams params;
    private StreamInput streamInput;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws IOException {
        MLCommonsClassLoader.loadClassMapping();

        params = new SampleAlgoParams(11);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);
        streamInput = bytesStreamOutput.bytes().streamInput();
    }

    @Test
    public void testClassLoader_SampleAlgoParams() {
        SampleAlgoParams sampleAlgoParams = MLCommonsClassLoader.initInstance(FunctionName.SAMPLE_ALGO, streamInput, StreamInput.class);
        assertEquals(params.getSampleParam(), sampleAlgoParams.getSampleParam());
    }

    @Test
    public void testClassLoader_Return_MLAlgoParams() {
        MLAlgoParams mlAlgoParams = MLCommonsClassLoader.initInstance(FunctionName.SAMPLE_ALGO, streamInput, StreamInput.class);
        assertTrue(mlAlgoParams instanceof SampleAlgoParams);
        assertEquals(params.getSampleParam(), ((SampleAlgoParams)mlAlgoParams).getSampleParam());
    }

    @Test
    public void testClassLoader_WrongType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Can't find class for type TEST");

        SampleAlgoParams mlAlgoParams = MLCommonsClassLoader.initInstance(TestEnum.TEST, streamInput, StreamInput.class);
        assertEquals(params.getSampleParam(), mlAlgoParams.getSampleParam());
    }

    public enum TestEnum {
        TEST
    }

}

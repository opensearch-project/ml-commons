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
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorInput;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorOutput;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.Output;
import org.opensearch.ml.common.parameter.SampleAlgoParams;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MLCommonsClassLoaderTests {

    private SampleAlgoParams params;
    private StreamInput streamInputForParams;
    private Input input;
    private StreamInput streamInputForInput;
    private Output output;
    private StreamInput streamInputForOutput;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws IOException {
        MLCommonsClassLoader.loadClassMapping();

        params = new SampleAlgoParams(11);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);
        streamInputForParams = bytesStreamOutput.bytes().streamInput();

        input = new LocalSampleCalculatorInput("sum", Arrays.asList(1.0, 2.0, 3.0));
        BytesStreamOutput bytesStreamOutputForCalculatorInput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutputForCalculatorInput);
        streamInputForInput = bytesStreamOutputForCalculatorInput.bytes().streamInput();

        output = new LocalSampleCalculatorOutput(6.0);
        BytesStreamOutput bytesStreamOutputForCalculatorOutput = new BytesStreamOutput();
        output.writeTo(bytesStreamOutputForCalculatorOutput);
        streamInputForOutput = bytesStreamOutputForCalculatorOutput.bytes().streamInput();
    }

    @Test
    public void testClassLoader_SampleAlgoParams() {
        SampleAlgoParams sampleAlgoParams = MLCommonsClassLoader.initMLInstance(FunctionName.SAMPLE_ALGO, streamInputForParams, StreamInput.class);
        assertEquals(params.getSampleParam(), sampleAlgoParams.getSampleParam());
    }

    @Test
    public void testClassLoader_Return_MLAlgoParams() {
        MLAlgoParams mlAlgoParams = MLCommonsClassLoader.initMLInstance(FunctionName.SAMPLE_ALGO, streamInputForParams, StreamInput.class);
        assertTrue(mlAlgoParams instanceof SampleAlgoParams);
        assertEquals(params.getSampleParam(), ((SampleAlgoParams)mlAlgoParams).getSampleParam());
    }

    @Test
    public void testClassLoader_WrongType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Can't find class for type TEST");

        SampleAlgoParams mlAlgoParams = MLCommonsClassLoader.initMLInstance(TestEnum.TEST, streamInputForParams, StreamInput.class);
        assertEquals(params.getSampleParam(), mlAlgoParams.getSampleParam());
    }

    @Test
    public void testClassLoader_ExecuteInput() {
        LocalSampleCalculatorInput calculatorInput = MLCommonsClassLoader.initExecuteInputInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, streamInputForInput, StreamInput.class);
        assertEquals(this.input, calculatorInput);
    }

    @Test
    public void testClassLoader_ExecuteOutput() {
        LocalSampleCalculatorOutput calculatorOutput = MLCommonsClassLoader.initExecuteOutputInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, streamInputForOutput, StreamInput.class);
        assertEquals(this.output, calculatorOutput);
    }

    public enum TestEnum {
        TEST
    }

}

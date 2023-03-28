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
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.input.nlp.TextDocsMLInput;
import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.input.parameter.sample.SampleAlgoParams;
import org.opensearch.search.SearchModule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    @Test
    public void testClassLoader_MLInput() throws IOException {
        assertTrue(MLCommonsClassLoader.canInitMLInput(FunctionName.TEXT_EMBEDDING));

        String jsonStr = "{\"text_docs\":[\"doc1\",\"doc2\"],\"result_filter\":{\"return_bytes\":true,\"return_number\":true,\"target_response\":[\"field1\"], \"target_response_positions\": [2]}}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();

        TextDocsMLInput mlInput = MLCommonsClassLoader.initMLInput(FunctionName.TEXT_EMBEDDING, new Object[]{parser, FunctionName.TEXT_EMBEDDING}, XContentParser.class, FunctionName.class);
        assertNotNull(mlInput);
        assertEquals(FunctionName.TEXT_EMBEDDING, mlInput.getFunctionName());
        assertEquals(2, ((TextDocsInputDataSet)mlInput.getInputDataset()).getDocs().size());
    }

    public enum TestEnum {
        TEST
    }

}

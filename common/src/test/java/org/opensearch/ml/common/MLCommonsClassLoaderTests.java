/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.input.nlp.TextDocsMLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.sample.SampleAlgoParams;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensor;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;
import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;
import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
import org.opensearch.search.SearchModule;

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
        SampleAlgoParams sampleAlgoParams = MLCommonsClassLoader
            .initMLInstance(FunctionName.SAMPLE_ALGO, streamInputForParams, StreamInput.class);
        assertEquals(params.getSampleParam(), sampleAlgoParams.getSampleParam());
    }

    @Test
    public void testClassLoader_Return_MLAlgoParams() {
        MLAlgoParams mlAlgoParams = MLCommonsClassLoader.initMLInstance(FunctionName.SAMPLE_ALGO, streamInputForParams, StreamInput.class);
        assertTrue(mlAlgoParams instanceof SampleAlgoParams);
        assertEquals(params.getSampleParam(), ((SampleAlgoParams) mlAlgoParams).getSampleParam());
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
        LocalSampleCalculatorInput calculatorInput = MLCommonsClassLoader
            .initExecuteInputInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, streamInputForInput, StreamInput.class);
        assertEquals(this.input, calculatorInput);
    }

    @Test
    public void testClassLoader_ExecuteOutput() {
        LocalSampleCalculatorOutput calculatorOutput = MLCommonsClassLoader
            .initExecuteOutputInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, streamInputForOutput, StreamInput.class);
        assertEquals(this.output, calculatorOutput);
    }

    @Test
    public void testClassLoader_ExecuteMCorrInput() throws IOException {
        List<float[]> inputData = new ArrayList<>();
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        Input mcorrInput = new MetricsCorrelationInput(inputData);
        BytesStreamOutput bytesStreamOutputMCorrInput = new BytesStreamOutput();
        mcorrInput.writeTo(bytesStreamOutputMCorrInput);
        StreamInput streamInputForMcorrInput = bytesStreamOutputMCorrInput.bytes().streamInput();
        MetricsCorrelationInput mcorrStreamInput = MLCommonsClassLoader
            .initExecuteInputInstance(FunctionName.METRICS_CORRELATION, streamInputForMcorrInput, StreamInput.class);
        assertArrayEquals(((MetricsCorrelationInput) mcorrInput).getInputData().toArray(), mcorrStreamInput.getInputData().toArray());

    }

    @Test
    public void testClassLoader_ExecuteOutputMCorr() throws IOException {
        List<MCorrModelTensors> outputs = new ArrayList<>();
        MCorrModelTensor mCorrModelTensor = MCorrModelTensor
            .builder()
            .event_pattern(new float[] { 1.0f, 2.0f, 3.0f })
            .event_window(new float[] { 4.0f, 5.0f, 6.0f })
            .suspected_metrics(new long[] { 1, 2 })
            .build();
        List<MCorrModelTensor> mlModelTensors = Arrays.asList(mCorrModelTensor);
        MCorrModelTensors modelTensors = MCorrModelTensors.builder().mCorrModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        MetricsCorrelationOutput output = MetricsCorrelationOutput.builder().modelOutput(outputs).build();
        BytesStreamOutput bytesStreamOutputMcorrOutput = new BytesStreamOutput();
        output.writeTo(bytesStreamOutputMcorrOutput);
        StreamInput streamInputForOutput = bytesStreamOutputMcorrOutput.bytes().streamInput();
        MetricsCorrelationOutput mcorrOutput = MLCommonsClassLoader
            .initExecuteOutputInstance(FunctionName.METRICS_CORRELATION, streamInputForOutput, StreamInput.class);

        assertEquals(1, mcorrOutput.getModelOutput().size());
        MCorrModelTensors testmodelTensors = mcorrOutput.getModelOutput().get(0);
        assertEquals(1, testmodelTensors.getMCorrModelTensors().size());
        MCorrModelTensor testmodelTensor = testmodelTensors.getMCorrModelTensors().get(0);
        float[] events = testmodelTensor.getEvent_pattern();
        long[] metrics = testmodelTensor.getSuspected_metrics();
        assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f }, events, 0.001f);
        assertArrayEquals(new long[] { 1, 2 }, metrics);
    }

    private void testClassLoader_MLInput_DlModel(FunctionName functionName) throws IOException {
        assertTrue(MLCommonsClassLoader.canInitMLInput(functionName));

        String jsonStr =
            "{\"text_docs\":[\"doc1\",\"doc2\"],\"result_filter\":{\"return_bytes\":true,\"return_number\":true,\"target_response\":[\"field1\"], \"target_response_positions\": [2]}}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        TextDocsMLInput mlInput = MLCommonsClassLoader
            .initMLInput(functionName, new Object[] { parser, functionName }, XContentParser.class, FunctionName.class);
        assertNotNull(mlInput);
        assertEquals(functionName, mlInput.getFunctionName());
        assertEquals(2, ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs().size());
    }

    @Test
    public void testClassLoader_MLInput() throws IOException {
        testClassLoader_MLInput_DlModel(FunctionName.TEXT_EMBEDDING);
        testClassLoader_MLInput_DlModel(FunctionName.SPARSE_TOKENIZE);
        testClassLoader_MLInput_DlModel(FunctionName.SPARSE_ENCODING);
    }

    public enum TestEnum {
        TEST
    }

}

package org.opensearch.ml.common.transport.execute;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;

public class MLExecuteTaskRequestTest {
    private Input exInput;
    private List<float[]> inputData;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        inputData = new ArrayList<>();
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        exInput = MetricsCorrelationInput.builder().inputData(inputData).build();

    }

    @Test
    public void writeTo_Success() throws IOException {

        MLExecuteTaskRequest request = MLExecuteTaskRequest.builder().functionName(FunctionName.METRICS_CORRELATION).input(exInput).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLExecuteTaskRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(FunctionName.METRICS_CORRELATION, request.getFunctionName());
        MetricsCorrelationInput inputDataset = (MetricsCorrelationInput) request.getInput();
        assertEquals(inputDataset.getInputData().size(), inputData.size());
        assertArrayEquals(inputDataset.getInputData().get(0), inputData.get(0), 0.001f);
    }

    @Test
    public void validate_Success() {
        MLExecuteTaskRequest request = MLExecuteTaskRequest.builder().functionName(FunctionName.METRICS_CORRELATION).input(exInput).build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullFunctionNane() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("functionName is marked non-null but is null");
        MLExecuteTaskRequest request = MLExecuteTaskRequest.builder().build();

        request.validate();
    }

    @Test
    public void validate_Exception_NullMLInput() {
        MLExecuteTaskRequest request = MLExecuteTaskRequest.builder().functionName(FunctionName.METRICS_CORRELATION).build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML input can't be null;", exception.getMessage());
    }
}

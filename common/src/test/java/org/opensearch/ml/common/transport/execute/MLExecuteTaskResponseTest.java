package org.opensearch.ml.common.transport.execute;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensor;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;
import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class MLExecuteTaskResponseTest {

    @Test
    public void writeTo_Success() throws IOException {
        List<MCorrModelTensors> outputs = new ArrayList<>();
        MCorrModelTensor mCorrModelTensor = MCorrModelTensor.builder()
                .event_pattern(new float[]{1.0f, 2.0f, 3.0f})
                .event_window(new float[]{4.0f, 5.0f, 6.0f})
                .suspected_metrics(new long[]{1, 2})
                .build();
        List<MCorrModelTensor> mlModelTensors = Arrays.asList(mCorrModelTensor);
        MCorrModelTensors modelTensors = MCorrModelTensors.builder().mCorrModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        MetricsCorrelationOutput output = MetricsCorrelationOutput.builder().modelOutput(outputs).build();
        MLExecuteTaskResponse response = MLExecuteTaskResponse.builder()
                .functionName(FunctionName.METRICS_CORRELATION)
                .output(output)
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        response = new MLExecuteTaskResponse(bytesStreamOutput.bytes().streamInput());
        MetricsCorrelationOutput mcorrOutputTest = (MetricsCorrelationOutput)response.getOutput();
        assertEquals(1, mcorrOutputTest.getModelOutput().size());
        MCorrModelTensors testmodelTensors = mcorrOutputTest.getModelOutput().get(0);
        assertEquals(1, testmodelTensors.getMCorrModelTensors().size());
        MCorrModelTensor testmodelTensor = testmodelTensors.getMCorrModelTensors().get(0);
        float[] events = testmodelTensor.getEvent_pattern();
        long[] metrics = testmodelTensor.getSuspected_metrics();
        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f}, events, 0.001f);
        assertArrayEquals(new long[]{1, 2}, metrics);
    }

    @Test
    public void fromActionResponse_WithMLPredictionTaskResponse() {
        List<MCorrModelTensors> outputs = new ArrayList<>();
        MCorrModelTensor mCorrModelTensor = MCorrModelTensor.builder()
                .event_pattern(new float[]{1.0f, 2.0f, 3.0f})
                .event_window(new float[]{4.0f, 5.0f, 6.0f})
                .suspected_metrics(new long[]{1, 2})
                .build();
        List<MCorrModelTensor> mlModelTensors = Arrays.asList(mCorrModelTensor);
        MCorrModelTensors modelTensors = MCorrModelTensors.builder().mCorrModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        MetricsCorrelationOutput output = MetricsCorrelationOutput.builder().modelOutput(outputs).build();
        MLExecuteTaskResponse response = MLExecuteTaskResponse.builder()
                .functionName(FunctionName.METRICS_CORRELATION)
                .output(output)
                .build();
        assertSame(response, MLExecuteTaskResponse.fromActionResponse(response));
    }

    @Test
    public void toXContentTest() throws IOException {
        List<MCorrModelTensors> outputs = new ArrayList<>();
        MCorrModelTensor mCorrModelTensor = MCorrModelTensor.builder()
                .event_pattern(new float[]{1.0f, 2.0f, 3.0f})
                .event_window(new float[]{4.0f, 5.0f, 6.0f})
                .suspected_metrics(new long[]{1, 2})
                .build();
        List<MCorrModelTensor> mlModelTensors = Arrays.asList(mCorrModelTensor);
        MCorrModelTensors modelTensors = MCorrModelTensors.builder().mCorrModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        MetricsCorrelationOutput output = MetricsCorrelationOutput.builder().modelOutput(outputs).build();
        MLExecuteTaskResponse response = MLExecuteTaskResponse.builder()
                .functionName(FunctionName.METRICS_CORRELATION)
                .output(output)
                .build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"inference_results\":[{" +
                "\"event_window\":[4.0,5.0,6.0]," +
                "\"event_pattern\":[1.0,2.0,3.0]," +
                "\"suspected_metrics\":[1,2]}]}", jsonStr);
    }
}

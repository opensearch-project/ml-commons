/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.metricscorrelation;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.output.MLOutputType;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensor;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;
import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class MetricsCorrelationOutputTest {

    MetricsCorrelationOutput output;

    long[] value;
    @Before
    public void setUp() {
        value = new long[]{1, 2, 3};
        List<MCorrModelTensors> outputs = new ArrayList<>();
        MCorrModelTensor mCorrModelTensor = MCorrModelTensor.builder()
                .event(new long[]{1, 2, 3})
                .range(new float[]{4.0f, 5.0f, 6.0f})
                .metrics(new float[]{1.0f, 2.0f})
                .build();
        List<MCorrModelTensor> mlModelTensors = Arrays.asList(mCorrModelTensor);
        MCorrModelTensors modelTensors = MCorrModelTensors.builder().mCorrModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        output = MetricsCorrelationOutput.builder().modelOutput(outputs).build();
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(output, parsedTensorOutput -> {
            assertEquals(1, parsedTensorOutput.getModelOutput().size());
            MCorrModelTensors modelTensors = parsedTensorOutput.getModelOutput().get(0);
            assertEquals(1, modelTensors.getMCorrModelTensors().size());
            MCorrModelTensor modelTensor = modelTensors.getMCorrModelTensors().get(0);
            long[] events = modelTensor.getEvent();
            float[] metrics = modelTensor.getMetrics();
            assertArrayEquals(value, events);
            assertArrayEquals(new float[]{1.0f, 2.0f}, metrics, 0.001f);

        });
    }

    @Test
    public void readInputStream_NullField() throws IOException {
        MetricsCorrelationOutput modelTensorOutput = MetricsCorrelationOutput.builder().build();
        readInputStream(modelTensorOutput, parsedTensorOutput -> {
            assertNull(parsedTensorOutput.getModelOutput());
        });
    }

    private void readInputStream(MetricsCorrelationOutput input, Consumer<MetricsCorrelationOutput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLOutputType outputType = streamInput.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.MODEL_TENSOR, outputType);
        verify.accept(new MetricsCorrelationOutput(streamInput));
    }
}

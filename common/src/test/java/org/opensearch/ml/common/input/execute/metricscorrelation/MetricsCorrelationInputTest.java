/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.metricscorrelation;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class MetricsCorrelationInputTest {

    MetricsCorrelationInput input;

    Function<XContentParser, MetricsCorrelationInput> function = parser -> {
        try {
            return MetricsCorrelationInput.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse MetricsCorrelationInput", e);
        }
    };

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        List<float[]> inputData = new ArrayList<>();
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        input = MetricsCorrelationInput.builder().inputData(inputData).build();
    }

    @Test
    public void constructor_NullOperation() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("empty input data");
        MetricsCorrelationInput.builder().build();
    }

    @Test
    public void constructor_variableLengthInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("All the input metrics sizes should be same");
        List<float[]> inputData = new ArrayList<>();
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f });
        inputData.add(new float[] { 1.0f, 2.0f, 3.0f, 4.0f });
        MetricsCorrelationInput.builder().inputData(inputData).build();

    }

    @Test
    public void readInputStream_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MetricsCorrelationInput parsedParams = new MetricsCorrelationInput(streamInput);

        assertEquals(input.getInputData().size(), parsedParams.getInputData().size());
    }

    @Test
    public void parse_MetricsCorrelationInput() throws IOException {
        TestHelper.testParse(input, function);
    }
}

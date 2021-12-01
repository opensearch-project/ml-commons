/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.parameter;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class LinearRegressionParamsTest {

    private Function<XContentParser, LinearRegressionParams> function = parser -> {
        try {
            return (LinearRegressionParams) LinearRegressionParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse LinearRegressionParams", e);
        }
    };

    LinearRegressionParams params;

    @Before
    public void setUp() {
        params = LinearRegressionParams
                .builder()
                .objectiveType(LinearRegressionParams.ObjectiveType.ABSOLUTE_LOSS)
                .optimizerType(LinearRegressionParams.OptimizerType.ADAM)
                .learningRate(0.1)
                .momentumType(LinearRegressionParams.MomentumType.NESTEROV)
                .momentumFactor(0.2)
                .epsilon(0.3)
                .beta1(0.4)
                .beta2(0.5)
                .decayRate(0.6)
                .epochs(1)
                .batchSize(2)
                .seed(3L)
                .target("test_target")
                .build();
    }

    @Test
    public void readInputStream_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        LinearRegressionParams parsedParams = new LinearRegressionParams(streamInput);
        assertEquals(params, parsedParams);
    }

    @Test
    public void readInputStream_Success_Empty() throws IOException {
        LinearRegressionParams linearRegressionParams = LinearRegressionParams.builder().build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        linearRegressionParams.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        LinearRegressionParams parsedParams = new LinearRegressionParams(streamInput);
        assertEquals(linearRegressionParams, parsedParams);
    }

    @Test
    public void parse_LinearRegressionParams() throws IOException {
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_EmptyLinearRegressionParams() throws IOException {
        TestHelper.testParse(LinearRegressionParams.builder().build(), function);
    }

    @Test
    public void parse_LinearRegressionParams_WrongExtraField() throws IOException {
        TestHelper.testParseFromString(params, "{\"objective\":\"ABSOLUTE_LOSS\",\"learning_rate\":0.1,\"wrong_field\":1.0}", function);
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.regression;

import static org.junit.Assert.assertEquals;
import static org.opensearch.ml.common.TestHelper.contentObjectToString;
import static org.opensearch.ml.common.TestHelper.testParseFromString;

import java.io.IOException;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class LinearRegressionParamsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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
    public void parse_PassIntValueToDoubleField() throws IOException {
        LinearRegressionParams params = LinearRegressionParams
            .builder()
            .objectiveType(LinearRegressionParams.ObjectiveType.ABSOLUTE_LOSS)
            .optimizerType(LinearRegressionParams.OptimizerType.ADAM)
            .learningRate(0.1)
            .momentumType(LinearRegressionParams.MomentumType.NESTEROV)
            .momentumFactor(0.2)
            .epsilon(3.0)
            .beta1(0.4)
            .beta2(0.5)
            .decayRate(0.6)
            .epochs(1)
            .batchSize(2)
            .seed(3L)
            .target("test_target")
            .build();
        String paramsStr = contentObjectToString(params);
        testParseFromString(params, paramsStr.replace("\"epsilon\":3.0,", "\"epsilon\":3,"), function);
    }

    @Test
    public void parse_InvalidParam_InvalidDoubleValue() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Double value passed as String");
        String paramsStr = contentObjectToString(params);
        testParseFromString(params, paramsStr.replace("\"epsilon\":0.3,", "\"epsilon\":\"0.3\","), function);
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

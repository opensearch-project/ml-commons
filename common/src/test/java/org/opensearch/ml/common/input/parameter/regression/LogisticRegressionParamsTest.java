/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.regression;

import static org.junit.Assert.assertEquals;
import static org.opensearch.ml.common.TestHelper.contentObjectToString;
import static org.opensearch.ml.common.TestHelper.testParseFromString;
import static org.opensearch.ml.common.input.parameter.regression.LogisticRegressionParams.PARSE_FIELD_NAME;

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

public class LogisticRegressionParamsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private Function<XContentParser, LogisticRegressionParams> function = parser -> {
        try {
            return (LogisticRegressionParams) LogisticRegressionParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse LogisticRegressionParams", e);
        }
    };

    private LogisticRegressionParams logisticRegressionParams;

    @Before
    public void setUp() {
        logisticRegressionParams = LogisticRegressionParams
            .builder()
            .objectiveType(LogisticRegressionParams.ObjectiveType.LOGMULTICLASS)
            .optimizerType(LogisticRegressionParams.OptimizerType.ADA_GRAD)
            .learningRate(0.1)
            .momentumType(LogisticRegressionParams.MomentumType.STANDARD)
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
        logisticRegressionParams.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        LogisticRegressionParams params = new LogisticRegressionParams(streamInput);
        assertEquals(params, logisticRegressionParams);
    }

    @Test
    public void parse_PassIntValueToDoubleField() throws IOException {
        String paramsStr = contentObjectToString(logisticRegressionParams);
        testParseFromString(logisticRegressionParams, paramsStr, function);
    }

    @Test
    public void parse_InvalidParam_InvalidDoubleValue() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Double value passed as String");
        String paramsStr = contentObjectToString(logisticRegressionParams);
        testParseFromString(logisticRegressionParams, paramsStr.replace("\"epsilon\":0.3,", "\"epsilon\":\"0.3\","), function);
    }

    @Test
    public void test_GetWriteableName() {
        assertEquals(logisticRegressionParams.getWriteableName(), PARSE_FIELD_NAME);
    }

    @Test
    public void test_GetVersion() {
        assertEquals(logisticRegressionParams.getVersion(), 1);
    }

    @Test
    public void readInputStream_Success_Empty() throws IOException {
        LogisticRegressionParams params = LogisticRegressionParams.builder().build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        LogisticRegressionParams parsedParams = new LogisticRegressionParams(streamInput);
        assertEquals(params, parsedParams);
    }

    @Test
    public void parse_LogisticRegressionParams() throws IOException {
        TestHelper.testParse(logisticRegressionParams, function);
    }

    @Test
    public void parse_EmptyLogisticRegressionParams() throws IOException {
        TestHelper.testParse(LogisticRegressionParams.builder().build(), function);
    }

    @Test
    public void parse_LogisticRegressionParams_WrongExtraField() throws IOException {
        TestHelper
            .testParseFromString(
                logisticRegressionParams,
                "{\"objective\":\"LOGMULTICLASS\",\"learning_rate\":0.1,\"wrong_field\":1.0}",
                function
            );
    }

}

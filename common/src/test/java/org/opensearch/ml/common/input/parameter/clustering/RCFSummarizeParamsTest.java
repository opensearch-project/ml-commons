/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.clustering;

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

public class RCFSummarizeParamsTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    RCFSummarizeParams params;
    private Function<XContentParser, RCFSummarizeParams> function = parser -> {
        try {
            return (RCFSummarizeParams) RCFSummarizeParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse RCFSummarizeParams", e);
        }
    };

    @Before
    public void setUp() {
        params = RCFSummarizeParams.builder().maxK(2).initialK(10).distanceType(RCFSummarizeParams.DistanceType.L1).build();
    }

    @Test
    public void parseRCFSummarizeParams() throws IOException {
        TestHelper.testParse(params, function);
    }

    @Test
    public void parseRCFSummarizeParamsExceptionOnInvalidDoubleValue() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("2.01 cannot be converted to Integer without data loss");
        String paramsStr = contentObjectToString(params);
        testParseFromString(params, paramsStr.replace("\"max_k\":2,", "\"max_k\":2.01,"), function);
    }

    @Test
    public void parseRCFSummarizeParamsExceptionOnInvalidDoubleString() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Integer value passed as String");
        String paramsStr = contentObjectToString(params);
        testParseFromString(params, paramsStr.replace("\"max_k\":2,", "\"max_k\":\"2.01\","), function);
    }

    @Test
    public void parseEmptyRCFSummarizeParams() throws IOException {
        TestHelper.testParse(RCFSummarizeParams.builder().build(), function);
    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(params);
    }

    @Test
    public void readInputStream_Success_EmptyParams() throws IOException {
        readInputStream(RCFSummarizeParams.builder().build());
    }

    private void readInputStream(RCFSummarizeParams params) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        RCFSummarizeParams parsedParams = new RCFSummarizeParams(streamInput);
        assertEquals(params, parsedParams);
    }
}

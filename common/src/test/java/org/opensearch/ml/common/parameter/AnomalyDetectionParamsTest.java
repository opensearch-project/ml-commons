/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
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

public class AnomalyDetectionParamsTest {

    AnomalyDetectionParams params;
    private Function<XContentParser, AnomalyDetectionParams> function = parser -> {
        try {
            return (AnomalyDetectionParams)AnomalyDetectionParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse AnomalyDetectionParams", e);
        }
    };

    @Before
    public void setUp() {
        params = AnomalyDetectionParams.builder()
                .kernelType(AnomalyDetectionParams.ADKernelType.POLY)
                .degree(2)
                .build();
    }

    @Test
    public void parse_AnomalyDetectionParams() throws IOException {
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_Emptyparse_AnomalyDetectionParams() throws IOException {
        TestHelper.testParse(AnomalyDetectionParams.builder().build(), function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(params);
    }

    @Test
    public void readInputStream_Success_EmptyParams() throws IOException {
        readInputStream(AnomalyDetectionParams.builder().build());
    }

    private void readInputStream(AnomalyDetectionParams params) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        AnomalyDetectionParams parsedParams = new AnomalyDetectionParams(streamInput);
        assertEquals(params, parsedParams);
    }
}

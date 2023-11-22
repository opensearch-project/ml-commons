/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.ad;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class AnomalyDetectionLibSVMParamsTest {

    AnomalyDetectionLibSVMParams params;
    private Function<XContentParser, AnomalyDetectionLibSVMParams> function = parser -> {
        try {
            return (AnomalyDetectionLibSVMParams) AnomalyDetectionLibSVMParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse AnomalyDetectionParams", e);
        }
    };

    @Before
    public void setUp() {
        params = AnomalyDetectionLibSVMParams
            .builder()
            .kernelType(AnomalyDetectionLibSVMParams.ADKernelType.POLY)
            .gamma(1.0)
            .nu(0.5)
            .cost(1.0)
            .coeff(0.1)
            .epsilon(0.2)
            .degree(2)
            .build();
    }

    @Test
    public void parse_AnomalyDetectionParams() throws IOException {
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_Emptyparse_AnomalyDetectionParams() throws IOException {
        TestHelper.testParse(AnomalyDetectionLibSVMParams.builder().build(), function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(params);
    }

    @Test
    public void readInputStream_Success_EmptyParams() throws IOException {
        readInputStream(AnomalyDetectionLibSVMParams.builder().build());
    }

    private void readInputStream(AnomalyDetectionLibSVMParams params) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        AnomalyDetectionLibSVMParams parsedParams = new AnomalyDetectionLibSVMParams(streamInput);
        assertEquals(params, parsedParams);
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.rcf;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class BatchRCFParamsTest {

    BatchRCFParams params;
    private Function<XContentParser, BatchRCFParams> function = parser -> {
        try {
            return (BatchRCFParams) BatchRCFParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse BatchRCFParams", e);
        }
    };

    @Before
    public void setUp() {
        params = BatchRCFParams.builder().numberOfTrees(10).shingleSize(8).sampleSize(256).outputAfter(32).trainingDataSize(200).build();
    }

    @Test
    public void parse_RCFParams() throws IOException {
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_EmptyRCFParams() throws IOException {
        TestHelper.testParse(BatchRCFParams.builder().build(), function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(params);
    }

    @Test
    public void readInputStream_Success_EmptyParams() throws IOException {
        readInputStream(BatchRCFParams.builder().build());
    }

    private void readInputStream(BatchRCFParams params) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        BatchRCFParams parsedParams = new BatchRCFParams(streamInput);
        assertEquals(params, parsedParams);
    }
}

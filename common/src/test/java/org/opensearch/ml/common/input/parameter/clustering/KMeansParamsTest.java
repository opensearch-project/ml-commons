/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.clustering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.TestHelper.contentObjectToString;
import static org.opensearch.ml.common.TestHelper.testParseFromString;

import java.io.IOException;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class KMeansParamsTest {
    KMeansParams params;
    private Function<XContentParser, KMeansParams> function = parser -> {
        try {
            return (KMeansParams) KMeansParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse KMeansParams", e);
        }
    };

    @Before
    public void setUp() {
        params = KMeansParams.builder().centroids(2).iterations(10).distanceType(KMeansParams.DistanceType.COSINE).build();
    }

    @Test
    public void parse_KMeansParams() throws IOException {
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_KMeansParams_InvalidDoubleValue() throws IOException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            String paramsStr = contentObjectToString(params);
            testParseFromString(params, paramsStr.replace("\"iterations\":10,", "\"iterations\":10.01,"), function);
        });
        assertEquals("10.01 cannot be converted to Integer without data loss", exception.getMessage());
    }

    @Test
    public void parse_KMeansParams_InvalidDoubleString() throws IOException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            String paramsStr = contentObjectToString(params);
            testParseFromString(params, paramsStr.replace("\"iterations\":10,", "\"iterations\":\"10.01\","), function);
        });
        assertEquals("Integer value passed as String", exception.getMessage());
    }

    @Test
    public void parse_EmptyKMeansParams() throws IOException {
        TestHelper.testParse(KMeansParams.builder().build(), function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(params);
    }

    @Test
    public void readInputStream_Success_EmptyParams() throws IOException {
        readInputStream(KMeansParams.builder().build());
    }

    private void readInputStream(KMeansParams params) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        params.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        KMeansParams parsedParams = new KMeansParams(streamInput);
        assertEquals(params, parsedParams);
    }
}

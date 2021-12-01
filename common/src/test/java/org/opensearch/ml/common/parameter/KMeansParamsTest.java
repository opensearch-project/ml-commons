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

public class KMeansParamsTest {

    KMeansParams params;
    private Function<XContentParser, KMeansParams> function = parser -> {
        try {
            return (KMeansParams)KMeansParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse KMeansParams", e);
        }
    };

    @Before
    public void setUp() {
        params = KMeansParams.builder()
                .centroids(2)
                .iterations(10)
                .distanceType(KMeansParams.DistanceType.COSINE)
                .build();
    }

    @Test
    public void parse_KMeansParams() throws IOException {
        TestHelper.testParse(params, function);
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

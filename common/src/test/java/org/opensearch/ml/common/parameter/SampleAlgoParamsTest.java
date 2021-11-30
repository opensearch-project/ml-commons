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
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

import java.io.IOException;
import java.util.function.Function;

public class SampleAlgoParamsTest {

    SampleAlgoParams params;

    private Function<XContentParser, SampleAlgoParams> function = parser -> {
        try {
            return (SampleAlgoParams)SampleAlgoParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse SampleAlgoParams", e);
        }
    };

    @Before
    public void setUp() {
        params = SampleAlgoParams.builder()
                .sampleParam(2)
                .build();
    }

    @Test
    public void parse_SampleAlgoParams() throws IOException {
        TestHelper.testParse(params, function);
    }

    @Test
    public void parse_EmptySampleAlgoParams() throws IOException {
        TestHelper.testParse(SampleAlgoParams.builder().build(), function);
    }

    @Test
    public void parse_WrongExtraField() throws IOException {
        String jsonStr = "{\"sample_param\":2, \"wrong_field\":1}";
        TestHelper.testParseFromString(params, jsonStr, function);
    }
}

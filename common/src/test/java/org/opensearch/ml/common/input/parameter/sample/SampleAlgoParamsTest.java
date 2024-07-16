/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.sample;

import java.io.IOException;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class SampleAlgoParamsTest {

    SampleAlgoParams params;

    private Function<XContentParser, SampleAlgoParams> function = parser -> {
        try {
            return (SampleAlgoParams) SampleAlgoParams.parse(parser);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse SampleAlgoParams", e);
        }
    };

    @Before
    public void setUp() {
        params = SampleAlgoParams.builder().sampleParam(2).build();
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

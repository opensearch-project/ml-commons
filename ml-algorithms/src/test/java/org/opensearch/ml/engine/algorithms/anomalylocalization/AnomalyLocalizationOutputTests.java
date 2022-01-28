/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;

import static org.junit.Assert.assertEquals;

public class AnomalyLocalizationOutputTests {

    private AnomalyLocalizationOutput output;

    @Before
    public void setup() {
        AnomalyLocalizationOutput.Entity entity = new AnomalyLocalizationOutput.Entity();
        entity.setKey(Arrays.asList("key1"));
        AnomalyLocalizationOutput.Bucket bucket = new AnomalyLocalizationOutput.Bucket();
        bucket.setEntities(Arrays.asList(entity));
        AnomalyLocalizationOutput.Result result = new AnomalyLocalizationOutput.Result();
        result.setBuckets(Arrays.asList(bucket));
        output = new AnomalyLocalizationOutput();
        output.getResults().put("agg", result);
    }

    @Test
    public void testWriteable() throws Exception {
        BytesStreamOutput out = new BytesStreamOutput();

        output.writeTo(out);
        AnomalyLocalizationOutput newOutput = new AnomalyLocalizationOutput(out.bytes().streamInput());

        assertEquals(output, newOutput);
    }

    @Test
    public void testXContent() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder();

        builder = output.toXContent(builder, null);

        String json = Strings.toString(builder);
        XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, json);
        AnomalyLocalizationOutput newOutput = AnomalyLocalizationOutput.parse(parser);

        assertEquals(output, newOutput);
    }
}

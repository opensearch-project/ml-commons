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

public class OutputTests {

    private Output output;

    @Before
    public void setup() {
        Output.Entity entity = new Output.Entity();
        entity.setKey(Arrays.asList("key1"));
        Output.Bucket bucket = new Output.Bucket();
        bucket.setEntities(Arrays.asList(entity));
        Output.Result result = new Output.Result();
        result.setBuckets(Arrays.asList(bucket));
        output = new Output();
        output.getResults().put("agg", result);
    }

    @Test
    public void testWriteable() throws Exception {
        BytesStreamOutput out = new BytesStreamOutput();

        output.writeTo(out);
        Output newOutput = new Output(out.bytes().streamInput());

        assertEquals(output, newOutput);
    }

    @Test
    public void testXContent() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder();

        builder = output.toXContent(builder, null);

        String json = Strings.toString(builder);
        XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, json);
        Output newOutput = Output.parse(parser);

        assertEquals(output, newOutput);
    }
}

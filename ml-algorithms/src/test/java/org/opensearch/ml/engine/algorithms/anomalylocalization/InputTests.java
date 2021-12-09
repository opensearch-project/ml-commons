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
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchModule;
import org.opensearch.search.aggregations.AggregationBuilders;

import static org.junit.Assert.assertEquals;

public class InputTests {

    @Test
    public void testXContentFullObject() throws Exception {
        Input input = new Input("indexName", Arrays.asList("attribute"), Arrays.asList(AggregationBuilders.max("max").field("field"),
                AggregationBuilders.min("min").field("field")), "@timestamp", 0L, 10L, 1L, 2, Optional.of(3L),
                Optional.of(QueryBuilders.matchAllQuery()));
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder = input.toXContent(builder, null);
        String json = Strings.toString(builder);

        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                false, Collections.emptyList()).getNamedXContents()), null, json);
        parser.nextToken();
        Input newInput = Input.parse(parser);

        assertEquals(input, newInput);
    }

    @Test
    public void testXContentMissingAnomalyStartFilter() throws Exception {
        Input input = new Input("indexName", Arrays.asList("attribute"), Arrays.asList(AggregationBuilders.max("max").field("field")),
                "@timestamp", 0L, 10L, 1L, 2, Optional.empty(), Optional.empty());
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder = input.toXContent(builder, null);
        String json = Strings.toString(builder);

        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                false, Collections.emptyList()).getNamedXContents()), null, json);
        parser.nextToken();
        Input newInput = Input.parse(parser);

        assertEquals(input, newInput);
    }

    @Test
    public void testWriteable() throws Exception {
        Input input = new Input("indexName", Arrays.asList("attribute"), Arrays.asList(AggregationBuilders.max("max").field("field"),
                AggregationBuilders.min("min").field("field")), "@timestamp", 0L, 10L, 1L, 2, Optional.of(3L),
                Optional.of(QueryBuilders.matchAllQuery()));

        BytesStreamOutput out = new BytesStreamOutput();
        input.writeTo(out);
        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(),
                new NamedWriteableRegistry(new SearchModule(Settings.EMPTY, false, Collections.emptyList()).getNamedWriteables()));
        Input newInput = new Input(in);

        assertEquals(input, newInput);
    }
}

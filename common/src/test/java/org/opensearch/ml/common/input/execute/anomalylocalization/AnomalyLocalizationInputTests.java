/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.anomalylocalization;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.NamedWriteableAwareStreamInput;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchModule;
import org.opensearch.search.aggregations.AggregationBuilders;

public class AnomalyLocalizationInputTests {

    @Test
    public void testXContentFullObject() throws Exception {
        AnomalyLocalizationInput input = new AnomalyLocalizationInput(
            "indexName",
            Arrays.asList("attribute"),
            Arrays.asList(AggregationBuilders.max("max").field("field"), AggregationBuilders.min("min").field("field")),
            "@timestamp",
            0L,
            10L,
            1L,
            2,
            Optional.of(3L),
            Optional.of(QueryBuilders.matchAllQuery())
        );
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder = input.toXContent(builder, null);
        String json = builder.toString();

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        AnomalyLocalizationInput newInput = AnomalyLocalizationInput.parse(parser);

        assertEquals(input, newInput);
    }

    @Test
    public void testXContentMissingAnomalyStartFilter() throws Exception {
        AnomalyLocalizationInput input = new AnomalyLocalizationInput(
            "indexName",
            Arrays.asList("attribute"),
            Arrays.asList(AggregationBuilders.max("max").field("field")),
            "@timestamp",
            0L,
            10L,
            1L,
            2,
            Optional.empty(),
            Optional.empty()
        );
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder = input.toXContent(builder, null);
        String json = builder.toString();

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        AnomalyLocalizationInput newInput = AnomalyLocalizationInput.parse(parser);

        assertEquals(input, newInput);
    }

    @Test
    public void testWriteable() throws Exception {
        AnomalyLocalizationInput input = new AnomalyLocalizationInput(
            "indexName",
            Arrays.asList("attribute"),
            Arrays.asList(AggregationBuilders.max("max").field("field"), AggregationBuilders.min("min").field("field")),
            "@timestamp",
            0L,
            10L,
            1L,
            2,
            Optional.of(3L),
            Optional.of(QueryBuilders.matchAllQuery())
        );

        BytesStreamOutput out = new BytesStreamOutput();
        input.writeTo(out);
        StreamInput in = new NamedWriteableAwareStreamInput(
            out.bytes().streamInput(),
            new NamedWriteableRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedWriteables())
        );
        AnomalyLocalizationInput newInput = new AnomalyLocalizationInput(in);

        assertEquals(input, newInput);
    }
}

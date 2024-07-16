/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

public class StopWordsTests {

    @Test
    public void writeTo() throws IOException {
        StopWords stopWords = new StopWords("test_index", List.of("test_field").toArray(new String[0]));
        BytesStreamOutput output = new BytesStreamOutput();
        stopWords.writeTo(output);
        StopWords stopWords1 = new StopWords(output.bytes().streamInput());

        Assert.assertEquals(stopWords.getIndex(), stopWords1.getIndex());
        Assert.assertArrayEquals(stopWords.getSourceFields(), stopWords1.getSourceFields());
    }

    @Test
    public void toXContent() throws IOException {
        StopWords stopWords = new StopWords("test_index", List.of("test_field").toArray(new String[0]));
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        stopWords.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertEquals("{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}", content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        StopWords stopWords = StopWords.parse(parser);

        Assert.assertEquals(stopWords.getIndex(), "test_index");
        Assert.assertArrayEquals(stopWords.getSourceFields(), List.of("test_field").toArray(new String[0]));
    }
}

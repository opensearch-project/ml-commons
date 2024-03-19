/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import org.junit.Assert;
import org.junit.Before;
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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class GuardrailTests {
    StopWords stopWords;
    String[] regex;

    @Before
    public void setUp() {
        stopWords = new StopWords("test_index", List.of("test_field").toArray(new String[0]));
        regex = List.of("regex1").toArray(new String[0]);
    }

    @Test
    public void writeTo() throws IOException {
        Guardrail guardrail = new Guardrail(List.of(stopWords), regex);
        BytesStreamOutput output = new BytesStreamOutput();
        guardrail.writeTo(output);
        Guardrail guardrail1 = new Guardrail(output.bytes().streamInput());

        Assert.assertArrayEquals(guardrail.getStopWords().toArray(), guardrail1.getStopWords().toArray());
        Assert.assertArrayEquals(guardrail.getRegex(), guardrail1.getRegex());
    }

    @Test
    public void toXContent() throws IOException {
        Guardrail guardrail = new Guardrail(List.of(stopWords), regex);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        guardrail.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertEquals("{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"regex1\"]}", content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"regex1\"]}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        Guardrail guardrail = Guardrail.parse(parser);

        Assert.assertArrayEquals(guardrail.getStopWords().toArray(), List.of(stopWords).toArray());
        Assert.assertArrayEquals(guardrail.getRegex(), regex);
    }
}
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

public class GuardrailsTests {
    StopWords stopWords;
    String[] regex;
    LocalRegexGuardrail inputLocalRegexGuardrail;
    LocalRegexGuardrail outputLocalRegexGuardrail;

    @Before
    public void setUp() {
        stopWords = new StopWords("test_index", List.of("test_field").toArray(new String[0]));
        regex = List.of("regex1").toArray(new String[0]);
        inputLocalRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
        outputLocalRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
    }

    @Test
    public void writeTo() throws IOException {
        Guardrails guardrails = new Guardrails("local_regex", inputLocalRegexGuardrail, outputLocalRegexGuardrail);
        BytesStreamOutput output = new BytesStreamOutput();
        guardrails.writeTo(output);
        Guardrails guardrails1 = new Guardrails(output.bytes().streamInput());

        Assert.assertEquals(guardrails.getType(), guardrails1.getType());
        Assert.assertEquals(guardrails.getInputGuardrail(), guardrails1.getInputGuardrail());
        Assert.assertEquals(guardrails.getOutputGuardrail(), guardrails1.getOutputGuardrail());
    }

    @Test
    public void toXContent() throws IOException {
        Guardrails guardrails = new Guardrails("local_regex", inputLocalRegexGuardrail, outputLocalRegexGuardrail);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        guardrails.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertEquals("{\"type\":\"local_regex\"," +
                "\"input_guardrail\":{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"regex1\"]}," +
                "\"output_guardrail\":{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"regex1\"]}}",
                content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"type\":\"local_regex\"," +
                "\"input_guardrail\":{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"regex1\"]}," +
                "\"output_guardrail\":{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"regex1\"]}}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        Guardrails guardrails = Guardrails.parse(parser);

        Assert.assertEquals(guardrails.getType(), "local_regex");
        Assert.assertEquals(guardrails.getInputGuardrail(), inputLocalRegexGuardrail);
        Assert.assertEquals(guardrails.getOutputGuardrail(), outputLocalRegexGuardrail);
    }
}
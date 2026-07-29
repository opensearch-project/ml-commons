/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agenticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class AgenticSearchTemplateTests {

    private AgenticSearchTemplate template;

    @Before
    public void setUp() {
        Map<String, Object> lexQuery = new LinkedHashMap<>();
        lexQuery.put("type", "string");
        lexQuery.put("required", true);
        lexQuery.put("description", "content words only");

        Map<String, Object> sortBy = new LinkedHashMap<>();
        sortBy.put("type", "string");
        sortBy.put("enum", java.util.List.of("price", "rating"));
        sortBy.put("source", "mapping");

        Map<String, Object> paramSchema = new LinkedHashMap<>();
        paramSchema.put("lex_query", lexQuery);
        paramSchema.put("sort_by", sortBy);

        template = AgenticSearchTemplate
            .builder()
            .templateId("product_search")
            .indexBinding("products")
            .description("Product search with filters/sort")
            .paramSchema(paramSchema)
            .createdTime(Instant.ofEpochMilli(1_700_000_000_000L))
            .lastUpdatedTime(Instant.ofEpochMilli(1_700_000_050_000L))
            .createdBy("alice")
            .build();
    }

    @Test
    public void streamRoundTrip_preservesAllFields() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        template.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        AgenticSearchTemplate parsed = new AgenticSearchTemplate(in);

        assertEquals(template.getTemplateId(), parsed.getTemplateId());
        assertEquals(template.getIndexBinding(), parsed.getIndexBinding());
        assertEquals(template.getDescription(), parsed.getDescription());
        assertEquals(template.getParamSchema(), parsed.getParamSchema());
        assertEquals(template.getCreatedTime(), parsed.getCreatedTime());
        assertEquals(template.getLastUpdatedTime(), parsed.getLastUpdatedTime());
        assertEquals(template.getCreatedBy(), parsed.getCreatedBy());
    }

    @Test
    public void xContentRoundTrip_preservesFields() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.JSON.contentBuilder();
        template.toXContent(builder, ToXContentObject.EMPTY_PARAMS);
        String json = builder.toString();

        XContentParser parser = MediaTypeRegistry.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        AgenticSearchTemplate parsed = AgenticSearchTemplate.parse(parser);

        assertEquals(template.getTemplateId(), parsed.getTemplateId());
        assertEquals(template.getIndexBinding(), parsed.getIndexBinding());
        assertEquals(template.getParamSchema(), parsed.getParamSchema());
        assertEquals(template.getCreatedTime(), parsed.getCreatedTime());
    }

    @Test
    public void streamRoundTrip_nullParamSchema() throws IOException {
        AgenticSearchTemplate minimal = AgenticSearchTemplate.builder().templateId("t1").build();
        BytesStreamOutput out = new BytesStreamOutput();
        minimal.writeTo(out);
        AgenticSearchTemplate parsed = new AgenticSearchTemplate(out.bytes().streamInput());
        assertEquals("t1", parsed.getTemplateId());
        assertFalse(parsed.hasParams());
    }

    @Test
    public void validation_templateIdAndParams() {
        assertTrue(template.isValidTemplateId());
        assertTrue(template.hasParams());
        assertFalse(AgenticSearchTemplate.builder().templateId("bad id with spaces").build().isValidTemplateId());
        assertFalse(AgenticSearchTemplate.builder().templateId("ok").build().hasParams());
    }
}

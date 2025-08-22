/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class IndexInsightConfigTests {

    @Test
    public void testBuilder() {
        IndexInsightConfig config = IndexInsightConfig.builder().isEnable(true).tenantId("test-tenant").build();

        assertEquals(true, config.getIsEnable());
        assertEquals("test-tenant", config.getTenantId());
    }

    @Test
    public void testBuilder_WithNullValues() {
        IndexInsightConfig config = IndexInsightConfig.builder().isEnable(null).tenantId(null).build();

        assertNull(config.getIsEnable());
        assertNull(config.getTenantId());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        IndexInsightConfig original = IndexInsightConfig.builder().isEnable(true).tenantId("test-tenant").build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        Boolean isEnable = input.readBoolean();
        String tenantId = input.readOptionalString();

        assertEquals(original.getIsEnable(), isEnable);
        assertEquals(original.getTenantId(), tenantId);
    }

    @Test
    public void testToXContent() throws IOException {
        IndexInsightConfig config = IndexInsightConfig.builder().isEnable(true).tenantId("test-tenant").build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        config.toXContent(builder, null);
        String json = builder.toString();

        assertTrue(json.contains("\"is_enable\":true"));
        assertTrue(json.contains("\"tenant_id\":\"test-tenant\""));
    }

    @Test
    public void testToXContent_WithNullTenantId() throws IOException {
        IndexInsightConfig config = IndexInsightConfig.builder().isEnable(true).tenantId(null).build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        config.toXContent(builder, null);
        String json = builder.toString();

        assertTrue(json.contains("\"is_enable\":true"));
        assertFalse(json.contains("tenant_id"));
    }

    @Test
    public void testParseFromXContent() throws IOException {
        String json = "{\"is_enable\": true,\"tenant_id\":\"test-tenant\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsightConfig config = IndexInsightConfig.parse(parser);

        assertEquals(true, config.getIsEnable());
        assertEquals("test-tenant", config.getTenantId());
    }

    @Test
    public void testParseFromXContent_WithMissingTenantId() throws IOException {
        String json = "{\"is_enable\":true}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsightConfig config = IndexInsightConfig.parse(parser);

        assertEquals(true, config.getIsEnable());
        assertNull(config.getTenantId());
    }

    @Test
    public void testParseFromXContent_WithUnknownField() throws IOException {
        String json = "{\"is_enable\":true,\"tenant_id\":\"test-tenant\",\"unknown_field\":\"value\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsightConfig config = IndexInsightConfig.parse(parser);

        assertEquals(true, config.getIsEnable());
        assertEquals("test-tenant", config.getTenantId());
    }

    @Test
    public void testParseFromXContent_WithEmptyObject() throws IOException {
        String json = "{}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsightConfig config = IndexInsightConfig.parse(parser);

        assertNull(config.getIsEnable());
        assertNull(config.getTenantId());
    }
}

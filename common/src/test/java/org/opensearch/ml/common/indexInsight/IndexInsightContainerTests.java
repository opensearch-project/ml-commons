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

public class IndexInsightContainerTests {

    @Test
    public void testBuilder() {
        IndexInsightContainer container = IndexInsightContainer.builder().indexName("test-index").tenantId("test-tenant").build();

        assertEquals("test-index", container.getIndexName());
        assertEquals("test-tenant", container.getTenantId());
    }

    @Test
    public void testBuilder_WithNullValues() {
        IndexInsightContainer container = IndexInsightContainer.builder().indexName(null).tenantId(null).build();

        assertNull(container.getIndexName());
        assertNull(container.getTenantId());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        IndexInsightContainer original = IndexInsightContainer.builder().indexName("test-index").tenantId("test-tenant").build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        String indexName = input.readString();
        String tenantId = input.readString();

        assertEquals(original.getIndexName(), indexName);
        assertEquals(original.getTenantId(), tenantId);
    }

    @Test
    public void testToXContent() throws IOException {
        IndexInsightContainer container = IndexInsightContainer.builder().indexName("test-index").tenantId("test-tenant").build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        container.toXContent(builder, null);
        String json = builder.toString();

        assertTrue(json.contains("\"index_name\":\"test-index\""));
        assertTrue(json.contains("\"tenant_id\":\"test-tenant\""));
    }

    @Test
    public void testToXContent_WithNullTenantId() throws IOException {
        IndexInsightContainer container = IndexInsightContainer.builder().indexName("test-index").tenantId(null).build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        container.toXContent(builder, null);
        String json = builder.toString();

        assertTrue(json.contains("\"index_name\":\"test-index\""));
        assertFalse(json.contains("tenant_id"));
    }

    @Test
    public void testParseFromXContent() throws IOException {
        String json = "{\"index_name\":\"test-index\",\"tenant_id\":\"test-tenant\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsightContainer container = IndexInsightContainer.parse(parser);

        assertEquals("test-index", container.getIndexName());
        assertEquals("test-tenant", container.getTenantId());
    }

    @Test
    public void testParseFromXContent_WithMissingTenantId() throws IOException {
        String json = "{\"index_name\":\"test-index\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsightContainer container = IndexInsightContainer.parse(parser);

        assertEquals("test-index", container.getIndexName());
        assertNull(container.getTenantId());
    }

    @Test
    public void testParseFromXContent_WithUnknownField() throws IOException {
        String json = "{\"index_name\":\"test-index\",\"tenant_id\":\"test-tenant\",\"unknown_field\":\"value\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsightContainer container = IndexInsightContainer.parse(parser);

        assertEquals("test-index", container.getIndexName());
        assertEquals("test-tenant", container.getTenantId());
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
        IndexInsightContainer container = IndexInsightContainer.parse(parser);

        assertNull(container.getIndexName());
        assertNull(container.getTenantId());
    }
}

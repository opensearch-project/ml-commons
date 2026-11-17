/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.CommonValue.VERSION_3_11_0;

import java.io.IOException;
import java.util.Collections;

import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
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

    @Test
    public void testStreamInputConstructor() throws IOException {
        IndexInsightConfig original = IndexInsightConfig.builder().isEnable(true).tenantId("test-tenant").build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        IndexInsightConfig deserialized = new IndexInsightConfig(input);

        assertEquals(original.getIsEnable(), deserialized.getIsEnable());
        assertEquals(original.getTenantId(), deserialized.getTenantId());
    }

    @Test
    public void testFromStream() throws IOException {
        IndexInsightConfig original = IndexInsightConfig.builder().isEnable(false).tenantId("default_id").build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        IndexInsightConfig deserialized = IndexInsightConfig.fromStream(input);

        assertEquals(original.getIsEnable(), deserialized.getIsEnable());
        assertEquals(original.getTenantId(), deserialized.getTenantId());
    }

    private XContentParser parserFor(String json) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        return parser;
    }

    @Test
    public void testParse_WithAgentId() throws IOException {
        IndexInsightConfig config = IndexInsightConfig.parse(parserFor("{\"is_enable\":true,\"agent_id\":\"agent-1\"}"), true);

        assertEquals(true, config.getIsEnable());
        assertEquals("agent-1", config.getAgentId());
    }

    @Test
    public void testParse_WithNullAgentId() throws IOException {
        IndexInsightConfig config = IndexInsightConfig.parse(parserFor("{\"is_enable\":true,\"agent_id\":null}"), true);

        assertNull(config.getAgentId());
    }

    @Test
    public void testParse_RejectUnknownFields() throws IOException {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> IndexInsightConfig.parse(parserFor("{\"is_enable\":true,\"agentId\":\"agent-1\"}"), true)
        );
        assertEquals("Unknown field [agentId] in index insight config", e.getMessage());
    }

    @Test
    public void testParse_RejectUnknownObjectField() throws IOException {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> IndexInsightConfig.parse(parserFor("{\"is_enable\":true,\"settings\":{\"a\":1}}"), true)
        );
        assertEquals("Unknown field [settings] in index insight config", e.getMessage());
    }

    @Test
    public void testToXContent_WithAgentId() throws IOException {
        IndexInsightConfig config = IndexInsightConfig.builder().isEnable(true).agentId("agent-1").build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        config.toXContent(builder, ToXContent.EMPTY_PARAMS);

        assertEquals("{\"is_enable\":true,\"agent_id\":\"agent-1\"}", builder.toString());
    }

    @Test
    public void testStream_WithAgentId() throws IOException {
        IndexInsightConfig original = IndexInsightConfig.builder().isEnable(true).tenantId("test-tenant").agentId("agent-1").build();

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(VERSION_3_11_0);
        original.writeTo(output);
        StreamInput input = output.bytes().streamInput();
        input.setVersion(VERSION_3_11_0);
        IndexInsightConfig deserialized = new IndexInsightConfig(input);

        assertEquals(true, deserialized.getIsEnable());
        assertEquals("test-tenant", deserialized.getTenantId());
        assertEquals("agent-1", deserialized.getAgentId());
    }

    @Test
    public void testStream_WithAgentId_ToOlderVersion() throws IOException {
        IndexInsightConfig original = IndexInsightConfig.builder().isEnable(true).tenantId("test-tenant").agentId("agent-1").build();

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(Version.V_3_0_0);
        original.writeTo(output);
        StreamInput input = output.bytes().streamInput();
        input.setVersion(Version.V_3_0_0);
        IndexInsightConfig deserialized = new IndexInsightConfig(input);

        assertEquals(true, deserialized.getIsEnable());
        assertEquals("test-tenant", deserialized.getTenantId());
        assertNull(deserialized.getAgentId());
        assertEquals(0, input.available());
    }
}

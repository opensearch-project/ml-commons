/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLToolSelectionConfigTest {

    private MLToolSelectionConfig parse(String jsonStr) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        return MLToolSelectionConfig.parse(parser);
    }

    @Test
    public void testParseAllFields() throws IOException {
        MLToolSelectionConfig config = parse(
            "{\"enabled\":true,\"pinned_tools\":[\"A\",\"B\"],\"top_k\":3,\"min_pool_size\":20,"
                + "\"max_dynamic_tools\":8,\"embedding_model_id\":\"my-model\"}"
        );
        assertEquals(Boolean.TRUE, config.getEnabled());
        assertEquals(List.of("A", "B"), config.getPinnedTools());
        assertEquals(Integer.valueOf(3), config.getTopK());
        assertEquals(Integer.valueOf(20), config.getMinPoolSize());
        assertEquals(Integer.valueOf(8), config.getMaxDynamicTools());
        assertEquals("my-model", config.getEmbeddingModelId());
    }

    @Test
    public void testUnsetFieldsStayNullSoBuiltInDefaultsApply() throws IOException {
        MLToolSelectionConfig config = parse("{\"top_k\":3}");
        assertNull(config.getEnabled());
        assertNull(config.getPinnedTools());
        assertEquals(Integer.valueOf(3), config.getTopK());
        assertNull(config.getMinPoolSize());
        assertNull(config.getMaxDynamicTools());
        assertNull(config.getEmbeddingModelId());
    }

    @Test
    public void testEmptyPinnedListIsKeptDistinctFromUnset() throws IOException {
        MLToolSelectionConfig config = parse("{\"pinned_tools\":[]}");
        assertEquals(List.of(), config.getPinnedTools());
    }

    @Test
    public void testEmptyObjectParsesToAllNulls() throws IOException {
        MLToolSelectionConfig config = parse("{}");
        assertNull(config.getEnabled());
        assertNull(config.getPinnedTools());
        assertNull(config.getTopK());
        assertNull(config.getMinPoolSize());
        assertNull(config.getMaxDynamicTools());
        assertNull(config.getEmbeddingModelId());
    }

    @Test
    public void testUnknownFieldIsIgnored() throws IOException {
        // Agents registered against a newer version must still parse on an older node.
        MLToolSelectionConfig config = parse("{\"top_k\":3,\"future_knob\":\"x\"}");
        assertEquals(Integer.valueOf(3), config.getTopK());
    }

    @Test
    public void testUnknownObjectFieldIsSkippedWholesale() throws IOException {
        MLToolSelectionConfig config = parse("{\"future_block\":{\"a\":{\"b\":1}},\"top_k\":3}");
        assertEquals(Integer.valueOf(3), config.getTopK());
    }

    @Test
    public void testValidationRejectsBadValues() {
        assertThrows(IllegalArgumentException.class, () -> MLToolSelectionConfig.builder().topK(0).build());
        assertThrows(IllegalArgumentException.class, () -> MLToolSelectionConfig.builder().minPoolSize(0).build());
        assertThrows(IllegalArgumentException.class, () -> MLToolSelectionConfig.builder().pinnedTools(List.of("A", " ")).build());
        assertThrows(IllegalArgumentException.class, () -> MLToolSelectionConfig.builder().maxDynamicTools(0).build());
        assertThrows(IllegalArgumentException.class, () -> MLToolSelectionConfig.builder().embeddingModelId(" ").build());
    }

    @Test
    public void testValidationRejectsValuesAboveTheCeilings() {
        assertThrows(IllegalArgumentException.class, () -> MLToolSelectionConfig.builder().topK(51).build());
        assertThrows(IllegalArgumentException.class, () -> MLToolSelectionConfig.builder().minPoolSize(10001).build());
        assertThrows(IllegalArgumentException.class, () -> MLToolSelectionConfig.builder().maxDynamicTools(101).build());
    }

    @Test
    public void testValidationAcceptsTheCeilings() {
        MLToolSelectionConfig config = MLToolSelectionConfig.builder().topK(50).minPoolSize(10000).maxDynamicTools(100).build();
        assertEquals(Integer.valueOf(100), config.getMaxDynamicTools());
    }

    @Test
    public void testValidationRejectsBadValuesFromXContentToo() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"top_k\":0}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"pinned_tools\":[\"\"]}"));
        // A JSON null entry must be a validation error (400), not an NPE (500).
        assertThrows(IllegalArgumentException.class, () -> parse("{\"pinned_tools\":[\"A\",null]}"));
    }

    @Test
    public void testPinnedToolsIsDefensivelyCopied() {
        List<String> pinned = new ArrayList<>(List.of("A"));
        MLToolSelectionConfig config = MLToolSelectionConfig.builder().pinnedTools(pinned).build();
        pinned.add("B");
        assertEquals(List.of("A"), config.getPinnedTools());
    }

    @Test
    public void testStreamRoundTrip() throws IOException {
        MLToolSelectionConfig original = MLToolSelectionConfig
            .builder()
            .enabled(true)
            .pinnedTools(List.of("A", "B"))
            .topK(4)
            .minPoolSize(12)
            .maxDynamicTools(9)
            .embeddingModelId("my-model")
            .build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        assertEquals(original, new MLToolSelectionConfig(out.bytes().streamInput()));
    }

    @Test
    public void testStreamRoundTripWithAllNulls() throws IOException {
        MLToolSelectionConfig original = MLToolSelectionConfig.builder().build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        MLToolSelectionConfig restored = new MLToolSelectionConfig(out.bytes().streamInput());
        assertEquals(original, restored);
        assertNull(restored.getEnabled());
        assertNull(restored.getPinnedTools());
        assertNull(restored.getTopK());
        assertNull(restored.getMinPoolSize());
        assertNull(restored.getMaxDynamicTools());
        assertNull(restored.getEmbeddingModelId());
    }

    @Test
    public void testStreamRoundTripKeepsEmptyPinnedList() throws IOException {
        MLToolSelectionConfig original = MLToolSelectionConfig.builder().pinnedTools(List.of()).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        MLToolSelectionConfig restored = new MLToolSelectionConfig(out.bytes().streamInput());
        assertEquals(List.of(), restored.getPinnedTools());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        MLToolSelectionConfig original = MLToolSelectionConfig
            .builder()
            .enabled(false)
            .pinnedTools(List.of("A"))
            .topK(6)
            .maxDynamicTools(4)
            .embeddingModelId("my-model")
            .build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertEquals(original, parse(BytesReference.bytes(builder).utf8ToString()));
    }

    @Test
    public void testXContentOmitsUnsetFields() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        MLToolSelectionConfig.builder().topK(6).build().toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertEquals("{\"top_k\":6}", BytesReference.bytes(builder).utf8ToString());
    }

    @Test
    public void testXContentWritesPinnedToolsAsArray() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        MLToolSelectionConfig.builder().pinnedTools(List.of("A", "B")).build().toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertTrue(BytesReference.bytes(builder).utf8ToString().contains("\"pinned_tools\":[\"A\",\"B\"]"));
    }

    @Test
    public void testExplicitNullsMeanUseTheDefault() throws IOException {
        MLToolSelectionConfig config = parse(
            "{\"enabled\":null,\"pinned_tools\":null,\"top_k\":null,\"min_pool_size\":null,"
                + "\"max_dynamic_tools\":null,\"embedding_model_id\":null,\"future_knob\":null}"
        );
        assertNull(config.getEnabled());
        assertNull(config.getPinnedTools());
        assertNull(config.getTopK());
        assertNull(config.getMinPoolSize());
        assertNull(config.getMaxDynamicTools());
        assertNull(config.getEmbeddingModelId());
    }
}

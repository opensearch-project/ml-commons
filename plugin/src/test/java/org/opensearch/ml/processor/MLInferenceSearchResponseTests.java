/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentGenerator;
import org.opensearch.search.SearchExtBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.test.OpenSearchTestCase;

public class MLInferenceSearchResponseTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    /**
     * Tests the toXContent method of MLInferenceSearchResponse with non-null parameters.
     * This test ensures that the method correctly serializes the response when parameters are present.
     *
     * @throws IOException if an I/O error occurs during the test
     */
    @Test
    public void testToXContent() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");

        SearchResponseSections internal = new SearchResponseSections(
            new SearchHits(new SearchHit[0], null, 0),
            null,
            null,
            false,
            false,
            null,
            0
        );
        MLInferenceSearchResponse searchResponse = new MLInferenceSearchResponse(
            params,
            internal,
            null,
            0,
            0,
            0,
            0,
            new ShardSearchFailure[0],
            MLInferenceSearchResponse.Clusters.EMPTY
        );

        XContent xc = mock(XContent.class);
        OutputStream os = mock(OutputStream.class);
        XContentGenerator generator = mock(XContentGenerator.class);
        when(xc.createGenerator(any(), any(), any(), anyBoolean())).thenReturn(generator);
        XContentBuilder builder = new XContentBuilder(xc, os);
        XContentBuilder actual = searchResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(actual);
    }

    /**
     * Tests the toXContent method of MLInferenceSearchResponse with null parameters.
     * This test verifies that the method handles null parameters correctly during serialization.
     *
     * @throws IOException if an I/O error occurs during the test
     */
    @Test
    public void testToXContentWithNullParams() throws IOException {
        SearchResponseSections internal = new SearchResponseSections(
            new SearchHits(new SearchHit[0], null, 0),
            null,
            null,
            false,
            false,
            null,
            0
        );
        MLInferenceSearchResponse searchResponse = new MLInferenceSearchResponse(
            null,
            internal,
            null,
            0,
            0,
            0,
            0,
            new ShardSearchFailure[0],
            MLInferenceSearchResponse.Clusters.EMPTY
        );

        XContent xc = mock(XContent.class);
        OutputStream os = mock(OutputStream.class);
        XContentGenerator generator = mock(XContentGenerator.class);
        when(xc.createGenerator(any(), any(), any(), anyBoolean())).thenReturn(generator);
        XContentBuilder builder = new XContentBuilder(xc, os);
        XContentBuilder actual = searchResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(actual);
    }

    /**
     * Tests the getParams method of MLInferenceSearchResponse.
     * This test ensures that the method correctly returns the parameters that were set during object creation.
     */
    @Test
    public void testGetParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");

        SearchResponseSections internal = new SearchResponseSections(
            new SearchHits(new SearchHit[0], null, 0),
            null,
            null,
            false,
            false,
            null,
            0
        );
        MLInferenceSearchResponse searchResponse = new MLInferenceSearchResponse(
            params,
            internal,
            null,
            0,
            0,
            0,
            0,
            new ShardSearchFailure[0],
            MLInferenceSearchResponse.Clusters.EMPTY
        );

        assertEquals(params, searchResponse.getParams());
    }

    /**
     * Tests the setParams method of MLInferenceSearchResponse.
     * This test verifies that the method correctly updates the parameters of the response object.
     */
    @Test
    public void testSetParams() {
        SearchResponseSections internal = new SearchResponseSections(
            new SearchHits(new SearchHit[0], null, 0),
            null,
            null,
            false,
            false,
            null,
            0
        );
        MLInferenceSearchResponse searchResponse = new MLInferenceSearchResponse(
            null,
            internal,
            null,
            0,
            0,
            0,
            0,
            new ShardSearchFailure[0],
            MLInferenceSearchResponse.Clusters.EMPTY
        );

        Map<String, Object> newParams = new HashMap<>();
        newParams.put("key3", "value3");
        searchResponse.setParams(newParams);

        assertEquals(newParams, searchResponse.getParams());
    }

    /**
     * The ml_inference output and the ext entries of other response processors, such as agentic_context,
     * must end up in one ext object, whatever the order of the processors.
     */
    @Test
    public void testToXContentMergesWithExistingExtensions() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");

        Map<String, Object> ext = toMap(responseJson(params, List.of(new TestExtBuilder("memory_id", "m1"))));

        Map<String, Object> extSection = (Map<String, Object>) ext.get("ext");
        assertEquals("m1", extSection.get("memory_id"));
        assertEquals(params, extSection.get("ml_inference"));
    }

    @Test
    public void testToXContentWritesOnlyMLInferenceExtension() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");

        Map<String, Object> response = toMap(responseJson(params, List.of()));

        Map<String, Object> extSection = (Map<String, Object>) response.get("ext");
        assertEquals(Map.of("ml_inference", params), extSection);
    }

    @Test
    public void testToXContentKeepsExistingExtensionsWithoutParams() throws IOException {
        Map<String, Object> response = toMap(responseJson(null, List.of(new TestExtBuilder("memory_id", "m1"))));

        assertEquals(Map.of("memory_id", "m1"), response.get("ext"));
    }

    @Test
    public void testToXContentWithoutAnyExtension() throws IOException {
        Map<String, Object> response = toMap(responseJson(null, List.of()));

        assertFalse(response.containsKey("ext"));
    }

    private String responseJson(Map<String, Object> params, List<SearchExtBuilder> extensions) throws IOException {
        SearchResponseSections internal = new SearchResponseSections(
            new SearchHits(new SearchHit[0], null, 0),
            null,
            null,
            false,
            false,
            null,
            0,
            extensions
        );
        MLInferenceSearchResponse searchResponse = new MLInferenceSearchResponse(
            params,
            internal,
            null,
            0,
            0,
            0,
            0,
            new ShardSearchFailure[0],
            MLInferenceSearchResponse.Clusters.EMPTY
        );
        XContentBuilder builder = XContentFactory.jsonBuilder();
        searchResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return builder.toString();
    }

    // The JSON parser rejects duplicate keys, so a second ext object fails here.
    private Map<String, Object> toMap(String json) {
        return XContentHelper.convertToMap(JsonXContent.jsonXContent, json, false);
    }

    private static class TestExtBuilder extends SearchExtBuilder {
        private final String name;
        private final String value;

        TestExtBuilder(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getWriteableName() {
            return "test_ext";
        }

        @Override
        public void writeTo(StreamOutput out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.field(name, value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestExtBuilder && name.equals(((TestExtBuilder) obj).name) && value.equals(((TestExtBuilder) obj).value);
        }
    }
}

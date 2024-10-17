/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentGenerator;
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
        when(xc.createGenerator(any(), any(), any())).thenReturn(generator);
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
        when(xc.createGenerator(any(), any(), any())).thenReturn(generator);
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
}

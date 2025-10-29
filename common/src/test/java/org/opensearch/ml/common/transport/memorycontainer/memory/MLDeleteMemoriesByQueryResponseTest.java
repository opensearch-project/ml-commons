/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.BulkByScrollTask;

/**
 * Unit tests for MLDeleteMemoriesByQueryResponse
 */
public class MLDeleteMemoriesByQueryResponseTest {

    private BulkByScrollResponse mockBulkResponse;
    private BulkByScrollTask.Status mockStatus;
    private MLDeleteMemoriesByQueryResponse response;

    @Before
    public void setUp() {
        mockBulkResponse = mock(BulkByScrollResponse.class);
        mockStatus = mock(BulkByScrollTask.Status.class);

        // Setup basic mock behavior
        when(mockBulkResponse.getTook()).thenReturn(TimeValue.timeValueMillis(1000));
        when(mockBulkResponse.isTimedOut()).thenReturn(false);
        when(mockBulkResponse.getDeleted()).thenReturn(10L);
        when(mockBulkResponse.getBatches()).thenReturn(1);
        when(mockBulkResponse.getVersionConflicts()).thenReturn(0L);
        when(mockBulkResponse.getNoops()).thenReturn(0L);
        when(mockBulkResponse.getBulkRetries()).thenReturn(0L);
        when(mockBulkResponse.getSearchRetries()).thenReturn(0L);
        when(mockBulkResponse.getStatus()).thenReturn(mockStatus);
        when(mockBulkResponse.getBulkFailures()).thenReturn(Collections.emptyList());
        when(mockBulkResponse.getSearchFailures()).thenReturn(Collections.emptyList());

        // Additional fields for BulkByScrollResponse.toXContent()
        when(mockBulkResponse.getTotal()).thenReturn(10L);
        when(mockBulkResponse.getUpdated()).thenReturn(0L);
        when(mockBulkResponse.getCreated()).thenReturn(0L);

        // Setup status mock
        when(mockStatus.getThrottled()).thenReturn(TimeValue.timeValueMillis(0));
        when(mockStatus.getThrottledUntil()).thenReturn(TimeValue.timeValueMillis(0));
        when(mockStatus.getRequestsPerSecond()).thenReturn(-1.0f);
    }

    @Test
    public void testConstructorWithBulkResponse() {
        response = new MLDeleteMemoriesByQueryResponse(mockBulkResponse);
        assertNotNull(response);
        assertEquals(mockBulkResponse, response.getBulkResponse());
    }

    @Test
    public void testToXContentDelegatesToBulkResponse() throws IOException {
        response = new MLDeleteMemoriesByQueryResponse(mockBulkResponse);

        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);

        // Verify that BulkByScrollResponse.toXContent was called
        verify(mockBulkResponse).toXContent(any(XContentBuilder.class), any(ToXContent.Params.class));

        String json = BytesReference.bytes(builder).utf8ToString();
        assertNotNull(json);
        // Should have opening and closing braces from our wrapper
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
    }

    @Test
    public void testSerializationDeserialization() throws IOException {
        // Create a real BulkByScrollResponse for proper serialization
        BulkByScrollResponse realResponse = new BulkByScrollResponse(Collections.emptyList(), null);

        MLDeleteMemoriesByQueryResponse originalResponse = new MLDeleteMemoriesByQueryResponse(realResponse);

        // Serialize
        BytesStreamOutput output = new BytesStreamOutput();
        originalResponse.writeTo(output);

        // Deserialize
        StreamInput input = output.bytes().streamInput();
        MLDeleteMemoriesByQueryResponse deserializedResponse = new MLDeleteMemoriesByQueryResponse(input);

        assertNotNull(deserializedResponse);
        assertNotNull(deserializedResponse.getBulkResponse());
        assertEquals(originalResponse.getBulkResponse().getDeleted(), deserializedResponse.getBulkResponse().getDeleted());
    }

    @Test
    public void testGetBulkResponse() {
        response = new MLDeleteMemoriesByQueryResponse(mockBulkResponse);
        assertEquals(mockBulkResponse, response.getBulkResponse());
    }

    @Test
    public void testWriteTo() throws IOException {
        response = new MLDeleteMemoriesByQueryResponse(mockBulkResponse);
        BytesStreamOutput output = new BytesStreamOutput();

        response.writeTo(output);

        // Verify that it delegates to bulkResponse.writeTo
        verify(mockBulkResponse).writeTo(any());
    }
}

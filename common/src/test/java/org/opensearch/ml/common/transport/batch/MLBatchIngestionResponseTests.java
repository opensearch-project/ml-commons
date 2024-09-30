/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.TestHelper;

public class MLBatchIngestionResponseTests {

    MLBatchIngestionResponse mlBatchIngestionResponse;

    @Before
    public void setUp() {
        mlBatchIngestionResponse = new MLBatchIngestionResponse("testId", MLTaskType.BATCH_INGEST, "Created");
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlBatchIngestionResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"task_id\":\"testId\",\"task_type\":\"BATCH_INGEST\",\"status\":\"Created\"}", content);
    }

    @Test
    public void readFromStream() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlBatchIngestionResponse.writeTo(output);

        MLBatchIngestionResponse response2 = new MLBatchIngestionResponse(output.bytes().streamInput());
        assertEquals("testId", response2.getTaskId());
        assertEquals("Created", response2.getStatus());
    }

    @Test
    public void fromActionResponseWithMLBatchIngestionResponseSuccess() {
        MLBatchIngestionResponse responseFromActionResponse = MLBatchIngestionResponse.fromActionResponse(mlBatchIngestionResponse);
        assertSame(mlBatchIngestionResponse, responseFromActionResponse);
        assertEquals(mlBatchIngestionResponse.getTaskType(), responseFromActionResponse.getTaskType());
    }

    @Test
    public void fromActionResponseSuccess() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlBatchIngestionResponse.writeTo(out);
            }
        };
        MLBatchIngestionResponse responseFromActionResponse = MLBatchIngestionResponse.fromActionResponse(actionResponse);
        assertNotSame(mlBatchIngestionResponse, responseFromActionResponse);
        assertEquals(mlBatchIngestionResponse.getTaskType(), responseFromActionResponse.getTaskType());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponseIOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLBatchIngestionResponse.fromActionResponse(actionResponse);
    }
}

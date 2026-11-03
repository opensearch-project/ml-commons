/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionResponse.TriggerStatus;

public class MLExecuteMemoryRetentionResponseTests {

    @Test
    public void testTriggeredStatusFlags() {
        assertTrue(TriggerStatus.TRIGGERED.isTriggered());
        assertEquals("triggered", TriggerStatus.TRIGGERED.getValue());

        assertFalse(TriggerStatus.ALREADY_RUNNING.isTriggered());
        assertEquals("already_running", TriggerStatus.ALREADY_RUNNING.getValue());

        assertFalse(TriggerStatus.RETENTION_DISABLED.isTriggered());
        assertEquals("retention_disabled", TriggerStatus.RETENTION_DISABLED.getValue());

        assertFalse(TriggerStatus.REMOTE_METADATA_STORE.isTriggered());
        assertEquals("remote_metadata_store", TriggerStatus.REMOTE_METADATA_STORE.getValue());
    }

    @Test
    public void testStreamRoundTripAllStatuses() throws IOException {
        for (TriggerStatus status : TriggerStatus.values()) {
            MLExecuteMemoryRetentionResponse response = new MLExecuteMemoryRetentionResponse(status, "msg-" + status.getValue());
            BytesStreamOutput out = new BytesStreamOutput();
            response.writeTo(out);

            StreamInput in = out.bytes().streamInput();
            MLExecuteMemoryRetentionResponse parsed = new MLExecuteMemoryRetentionResponse(in);
            assertEquals(status, parsed.getStatus());
            assertEquals("msg-" + status.getValue(), parsed.getMessage());
        }
    }

    @Test
    public void testStreamRoundTripNullMessage() throws IOException {
        MLExecuteMemoryRetentionResponse response = new MLExecuteMemoryRetentionResponse(TriggerStatus.TRIGGERED, null);
        BytesStreamOutput out = new BytesStreamOutput();
        response.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLExecuteMemoryRetentionResponse parsed = new MLExecuteMemoryRetentionResponse(in);
        assertEquals(TriggerStatus.TRIGGERED, parsed.getStatus());
        assertNull(parsed.getMessage());
    }

    @Test
    public void testToXContentTriggered() throws IOException {
        MLExecuteMemoryRetentionResponse response = new MLExecuteMemoryRetentionResponse(TriggerStatus.TRIGGERED, "started");
        String json = toJson(response);
        assertTrue(json.contains("\"acknowledged\":true"));
        assertTrue(json.contains("\"triggered\":true"));
        assertTrue(json.contains("\"status\":\"triggered\""));
        assertTrue(json.contains("\"message\":\"started\""));
    }

    @Test
    public void testToXContentAlreadyRunning() throws IOException {
        MLExecuteMemoryRetentionResponse response = new MLExecuteMemoryRetentionResponse(TriggerStatus.ALREADY_RUNNING, "busy");
        String json = toJson(response);
        // acknowledged reflects whether a run was actually triggered; a benign "already running" was not.
        assertTrue(json.contains("\"acknowledged\":false"));
        assertTrue(json.contains("\"triggered\":false"));
        assertTrue(json.contains("\"status\":\"already_running\""));
    }

    @Test
    public void testToXContentDisabled() throws IOException {
        MLExecuteMemoryRetentionResponse response = new MLExecuteMemoryRetentionResponse(TriggerStatus.RETENTION_DISABLED, "off");
        String json = toJson(response);
        assertTrue(json.contains("\"acknowledged\":false"));
        assertTrue(json.contains("\"triggered\":false"));
        assertTrue(json.contains("\"status\":\"retention_disabled\""));
    }

    @Test
    public void testToXContentOmitsNullMessage() throws IOException {
        MLExecuteMemoryRetentionResponse response = new MLExecuteMemoryRetentionResponse(TriggerStatus.TRIGGERED, null);
        String json = toJson(response);
        assertFalse(json.contains("\"message\""));
    }

    private String toJson(MLExecuteMemoryRetentionResponse response) throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return builder.toString();
    }
}

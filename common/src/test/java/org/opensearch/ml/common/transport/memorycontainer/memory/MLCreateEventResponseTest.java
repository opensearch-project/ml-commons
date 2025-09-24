/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLCreateEventResponseTest {

    @Test
    public void testBuilder() {
        MLCreateEventResponse response = MLCreateEventResponse.builder().eventId("event-123").sessionId("session-456").build();

        assertNotNull(response);
        assertEquals("event-123", response.getEventId());
        assertEquals("session-456", response.getSessionId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        MLCreateEventResponse response = MLCreateEventResponse.builder().eventId("event-abc").sessionId("session-def").build();

        BytesStreamOutput out = new BytesStreamOutput();
        response.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateEventResponse deserialized = new MLCreateEventResponse(in);

        assertEquals("event-abc", deserialized.getEventId());
        assertEquals("session-def", deserialized.getSessionId());
    }
}

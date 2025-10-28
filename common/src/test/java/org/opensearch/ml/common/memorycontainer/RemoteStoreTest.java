/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class RemoteStoreTest {

    @Test
    public void testRemoteStoreConstruction() {
        RemoteStore remoteStore = RemoteStore.builder().type("aoss").connectorId("ySf08JkBym-3qj1O2uub").build();

        assertNotNull(remoteStore);
        assertEquals("aoss", remoteStore.getType());
        assertEquals("ySf08JkBym-3qj1O2uub", remoteStore.getConnectorId());
    }

    @Test
    public void testRemoteStoreToXContent() throws IOException {
        RemoteStore remoteStore = RemoteStore.builder().type("aoss").connectorId("ySf08JkBym-3qj1O2uub").build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        remoteStore.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertNotNull(jsonStr);
        assert (jsonStr.contains("\"type\":\"aoss\""));
        assert (jsonStr.contains("\"connector_id\":\"ySf08JkBym-3qj1O2uub\""));
    }

    @Test
    public void testRemoteStoreParse() throws IOException {
        String json = "{\"type\":\"aoss\",\"connector_id\":\"ySf08JkBym-3qj1O2uub\"}";

        XContentParser parser = createParser(json);
        parser.nextToken();
        RemoteStore remoteStore = RemoteStore.parse(parser);

        assertNotNull(remoteStore);
        assertEquals("aoss", remoteStore.getType());
        assertEquals("ySf08JkBym-3qj1O2uub", remoteStore.getConnectorId());
    }

    @Test
    public void testRemoteStoreSerialization() throws IOException {
        RemoteStore remoteStore = RemoteStore.builder().type("aoss").connectorId("ySf08JkBym-3qj1O2uub").build();

        BytesStreamOutput output = new BytesStreamOutput();
        remoteStore.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RemoteStore deserializedRemoteStore = new RemoteStore(input);

        assertEquals(remoteStore.getType(), deserializedRemoteStore.getType());
        assertEquals(remoteStore.getConnectorId(), deserializedRemoteStore.getConnectorId());
    }

    private XContentParser createParser(String json) throws IOException {
        XContentParser parser = XContentType.JSON.xContent().createParser(null, null, json);
        return parser;
    }
}

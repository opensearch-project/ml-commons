/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class MLCreateConnectorResponseTests {

    @Test
    public void toXContent() throws IOException {
        MLCreateConnectorResponse response = new MLCreateConnectorResponse("testConnectorId");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"connector_id\":\"testConnectorId\"}", content);
    }

    @Test
    public void readFromStream() throws IOException {
        MLCreateConnectorResponse response = new MLCreateConnectorResponse("testConnectorId");
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);

        MLCreateConnectorResponse response2 = new MLCreateConnectorResponse(output.bytes().streamInput());
        assertEquals("testConnectorId", response2.getConnectorId());
    }


    @Test
    public void fromActionResponseWithMLCreateConnectorResponseSuccess() {
        MLCreateConnectorResponse response = new MLCreateConnectorResponse("testConnectorId");
        MLCreateConnectorResponse responseFromActionResponse = MLCreateConnectorResponse.fromActionResponse(response);
        assertSame(response, responseFromActionResponse);
        assertEquals(response.getConnectorId(), responseFromActionResponse.getConnectorId());
    }

    @Test
    public void fromActionResponseSuccess() {
        MLCreateConnectorResponse response = new MLCreateConnectorResponse("testConnectorId");
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLCreateConnectorResponse responseFromActionResponse = MLCreateConnectorResponse.fromActionResponse(actionResponse);
        assertNotSame(response, responseFromActionResponse);
        assertEquals(response.getConnectorId(), responseFromActionResponse.getConnectorId());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponseIOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLCreateConnectorResponse.fromActionResponse(actionResponse);
    }
}

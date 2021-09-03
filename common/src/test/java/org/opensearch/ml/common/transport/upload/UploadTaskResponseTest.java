/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.transport.upload;

import org.junit.Test;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.*;

public class UploadTaskResponseTest {

    @Test
    public void writeTo_Success() throws IOException {
        UploadTaskResponse response = UploadTaskResponse.builder()
            .modelId("modelId")
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        assertEquals(8, bytesStreamOutput.size());
        response = new UploadTaskResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals("modelId", response.getModelId());
    }

    @Test
    public void fromActionResponse_WithUploadTaskResponse() {
        UploadTaskResponse response = UploadTaskResponse.builder()
            .modelId("modelId")
            .build();
        assertSame(response, UploadTaskResponse.fromActionResponse(response));
    }

    @Test
    public void fromActionResponse_WithNonUploadTaskResponse() {
        UploadTaskResponse response = UploadTaskResponse.builder()
            .modelId("modelId")
            .build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        UploadTaskResponse result = UploadTaskResponse.fromActionResponse(actionResponse);
        assertNotSame(response, result);
        assertEquals(response.getModelId(), result.getModelId());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };

        UploadTaskResponse.fromActionResponse(actionResponse);
    }

    @Test
    public void toXContentTest() throws IOException {
        UploadTaskResponse response = UploadTaskResponse.builder()
            .modelId("b5009b99-268f-476d-a676-379a30f82457")
            .build();

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"model_id\":\"b5009b99-268f-476d-a676-379a30f82457\"}", jsonStr);
    }
}
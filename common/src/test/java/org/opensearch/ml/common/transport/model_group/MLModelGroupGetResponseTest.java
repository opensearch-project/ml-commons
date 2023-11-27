/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLModelGroup;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class MLModelGroupGetResponseTest {

    MLModelGroup mlModelGroup;

    @Before
    public void setUp() {
        mlModelGroup = MLModelGroup.builder()
                .name("modelGroup1")
                .latestVersion(1)
                .description("This is an example model group")
                .access("public")
                .build();
    }

    @Test
    public void writeTo_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLModelGroupGetResponse response = MLModelGroupGetResponse.builder().mlModelGroup(mlModelGroup).build();
        response.writeTo(bytesStreamOutput);
        MLModelGroupGetResponse parsedResponse = new MLModelGroupGetResponse(bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response.mlModelGroup, parsedResponse.mlModelGroup);
        assertEquals(response.mlModelGroup.getName(), parsedResponse.mlModelGroup.getName());
        assertEquals(response.mlModelGroup.getDescription(), parsedResponse.mlModelGroup.getDescription());
        assertEquals(response.mlModelGroup.getLatestVersion(), parsedResponse.mlModelGroup.getLatestVersion());
    }

    @Test
    public void toXContentTest() throws IOException {
        MLModelGroupGetResponse mlModelGroupGetResponse = MLModelGroupGetResponse.builder().mlModelGroup(mlModelGroup).build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlModelGroupGetResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"name\":\"modelGroup1\"," +
                "\"latest_version\":1," +
                "\"description\":\"This is an example model group\"," +
                "\"access\":\"public\"}",
                jsonStr);
    }

    @Test
    public void fromActionResponseWithMLModelGroupGetResponse_Success() {
        MLModelGroupGetResponse mlModelGroupGetResponse = MLModelGroupGetResponse.builder().mlModelGroup(mlModelGroup).build();
        MLModelGroupGetResponse mlModelGroupGetResponseFromActionResponse = MLModelGroupGetResponse.fromActionResponse(mlModelGroupGetResponse);
        assertSame(mlModelGroupGetResponse, mlModelGroupGetResponseFromActionResponse);
        assertEquals(mlModelGroupGetResponse.mlModelGroup, mlModelGroupGetResponseFromActionResponse.mlModelGroup);
    }

    @Test
    public void fromActionResponse_Success() {
        MLModelGroupGetResponse mlModelGroupGetResponse = MLModelGroupGetResponse.builder().mlModelGroup(mlModelGroup).build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlModelGroupGetResponse.writeTo(out);
            }
        };
        MLModelGroupGetResponse mlModelGroupGetResponseFromActionResponse = MLModelGroupGetResponse.fromActionResponse(actionResponse);
        assertNotSame(mlModelGroupGetResponse, mlModelGroupGetResponseFromActionResponse);
        assertNotEquals(mlModelGroupGetResponse.mlModelGroup, mlModelGroupGetResponseFromActionResponse.mlModelGroup);
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLModelGroupGetResponse.fromActionResponse(actionResponse);
    }
}

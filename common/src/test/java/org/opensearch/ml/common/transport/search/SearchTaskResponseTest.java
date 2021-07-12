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

package org.opensearch.ml.common.transport.search;

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
import static org.junit.Assert.assertEquals;

public class SearchTaskResponseTest {

    @Test
    public void writeTo_Success() throws IOException {
        SearchTaskResponse response = SearchTaskResponse.builder()
            .models("[]")
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        assertEquals(3, bytesStreamOutput.size());
        response = new SearchTaskResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals("[]", response.getModels());
    }

    @Test
    public void fromActionResponse_WithSearchTaskResponse() {
        SearchTaskResponse response = SearchTaskResponse.builder()
            .models("[]")
            .build();
        assertSame(response, SearchTaskResponse.fromActionResponse(response));
    }

    @Test
    public void fromActionResponse_WithNonSearchTaskResponse() {
        SearchTaskResponse response = SearchTaskResponse.builder()
            .models("[]")
            .build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        SearchTaskResponse result = SearchTaskResponse.fromActionResponse(actionResponse);
        assertNotSame(response, result);
        assertEquals(response.getModels(), result.getModels());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };

        SearchTaskResponse.fromActionResponse(actionResponse);
    }

    @Test
    public void toXContentTest() throws IOException {
        SearchTaskResponse response = SearchTaskResponse.builder()
            .models("[]")
            .build();

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"models\":\"[]\"}", jsonStr);
    }
}

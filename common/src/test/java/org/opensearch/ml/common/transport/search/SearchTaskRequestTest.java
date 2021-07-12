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
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.*;

public class SearchTaskRequestTest {

    @Test
    public void writeTo_Success() throws IOException {
        SearchTaskRequest request = SearchTaskRequest.builder()
            .modelId("modelId")
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        assertEquals(39, bytesStreamOutput.size());
        request = new SearchTaskRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals("modelId", request.getModelId());
        assertEquals("test", request.getName());
        assertEquals("pmml", request.getFormat());
        assertEquals("isolationforest", request.getAlgorithm());
    }

    @Test
    public void validate_Success() {
        SearchTaskRequest request = SearchTaskRequest.builder()
            .modelId("modelId")
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .build();

        assertNull(request.validate());
    }

    @Test
    public void fromActionRequest_Success_WithSearchTaskRequest() {
        SearchTaskRequest request = SearchTaskRequest.builder()
            .modelId("modelId")
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .build();

        assertSame(SearchTaskRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonSearchTaskRequest() {
        SearchTaskRequest request = SearchTaskRequest.builder()
            .modelId("modelId")
            .name("test")
            .format("pmml")
            .algorithm("isolationforest")
            .build();

        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        SearchTaskRequest result = SearchTaskRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getModelId(), result.getModelId());
        assertEquals(request.getName(), result.getName());
        assertEquals(request.getFormat(), result.getFormat());
        assertEquals(request.getAlgorithm(), result.getAlgorithm());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() throws IOException {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        SearchTaskRequest.fromActionRequest(actionRequest);
    }
}

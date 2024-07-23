package org.opensearch.ml.common.transport.search;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotSame;

public class MLSearchActionRequestTest {

    private SearchRequest searchRequest;

    @Before
    public void setUp() {
        searchRequest = new SearchRequest("test-index");
    }

    @Test
    public void testConstructorAndGetters() {
        MLSearchActionRequest request = MLSearchActionRequest.builder()
                .searchRequest(searchRequest)
                .tenantId("test-tenant")
                .build();
        assertEquals("test-index", request.getSearchRequest().indices()[0]);
        assertEquals("test-tenant", request.getTenantId());
    }

    @Test
    public void testStreamConstructorAndWriteTo() throws IOException {
        MLSearchActionRequest request = MLSearchActionRequest.builder()
                .searchRequest(searchRequest)
                .tenantId("test-tenant")
                .build();
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        MLSearchActionRequest deserializedRequest = new MLSearchActionRequest(out.bytes().streamInput());
        assertEquals("test-index", deserializedRequest.getSearchRequest().indices()[0]);
        assertEquals("test-tenant", deserializedRequest.getTenantId());
    }

    @Test
    public void testWriteToWithNullSearchRequest() throws IOException {
        MLSearchActionRequest request = MLSearchActionRequest.builder()
                .tenantId("test-tenant")
                .build();
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        MLSearchActionRequest deserializedRequest = new MLSearchActionRequest(out.bytes().streamInput());
        assertNull(deserializedRequest.getSearchRequest());
        assertEquals("test-tenant", deserializedRequest.getTenantId());
    }

    @Test
    public void testFromActionRequestWithMLSearchActionRequest() {
        MLSearchActionRequest request = MLSearchActionRequest.builder()
                .searchRequest(searchRequest)
                .tenantId("test-tenant")
                .build();
        MLSearchActionRequest result = MLSearchActionRequest.fromActionRequest(request);
        assertSame(result, request);
    }

    @Test
    public void testFromActionRequestWithNonMLSearchActionRequest() throws IOException {
        MLSearchActionRequest request = MLSearchActionRequest.builder()
                .searchRequest(searchRequest)
                .tenantId("test-tenant")
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

        MLSearchActionRequest result = MLSearchActionRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getSearchRequest().indices()[0], result.getSearchRequest().indices()[0]);
        assertEquals(request.getTenantId(), result.getTenantId());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequestIOException() {
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
        MLSearchActionRequest.fromActionRequest(actionRequest);
    }
}

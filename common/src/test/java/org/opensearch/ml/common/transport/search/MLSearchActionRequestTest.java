package org.opensearch.ml.common.transport.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLSearchActionRequestTest {

    private SearchRequest searchRequest;

    @Before
    public void setUp() {
        searchRequest = new SearchRequest("test-index");
    }

    @Test
    public void testSerializationDeserialization_Version_2_19_0() throws IOException {
        // Set up a valid SearchRequest
        SearchRequest searchRequest = new SearchRequest("test-index");

        // Create the MLSearchActionRequest
        MLSearchActionRequest originalRequest = MLSearchActionRequest
            .builder()
            .searchRequest(searchRequest)
            .tenantId("test-tenant")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(Version.V_2_19_0);
        originalRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(Version.V_2_19_0);
        MLSearchActionRequest deserializedRequest = new MLSearchActionRequest(in);

        assertEquals("test-tenant", deserializedRequest.getTenantId());
    }

    @Test
    public void testSerializationDeserialization_Version_2_18_0() throws IOException {

        // Create the MLSearchActionRequest
        MLSearchActionRequest originalRequest = MLSearchActionRequest
            .builder()
            .searchRequest(searchRequest)
            .tenantId("test-tenant")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(Version.V_2_18_0);
        originalRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(Version.V_2_18_0);
        MLSearchActionRequest deserializedRequest = new MLSearchActionRequest(in);

        assertNull(deserializedRequest.getTenantId());
    }

    @Test
    public void testFromActionRequest_WithMLSearchActionRequest() {
        MLSearchActionRequest request = MLSearchActionRequest.builder().searchRequest(searchRequest).tenantId("test-tenant").build();

        MLSearchActionRequest result = MLSearchActionRequest.fromActionRequest(request);

        assertSame(request, result);
    }

    @Test
    public void testFromActionRequest_WithSearchRequest() throws IOException {
        SearchRequest simpleRequest = new SearchRequest("test-index");

        MLSearchActionRequest result = MLSearchActionRequest.fromActionRequest(simpleRequest);

        assertNotNull(result);
        assertNull(result.getTenantId()); // Since tenantId wasn't in original request
    }

}

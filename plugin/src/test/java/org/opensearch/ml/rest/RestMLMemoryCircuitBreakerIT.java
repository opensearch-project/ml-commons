package org.opensearch.ml.rest;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.breaker.MemoryCircuitBreaker;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMLMemoryCircuitBreakerIT extends MLCommonsRestTestCase {
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        // restore the threshold to default value
        Response response1 = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.jvm_heap_memory_threshold\":"
                    + MemoryCircuitBreaker.DEFAULT_JVM_HEAP_USAGE_THRESHOLD
                    + "}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response1.getStatusLine().getStatusCode());
    }

    public void testRunWithMemoryCircuitBreaker() throws IOException {
        // set a low threshold
        Response response1 = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.jvm_heap_memory_threshold\":1}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response1.getStatusLine().getStatusCode());

        // expect task fail due to memory limit
        Exception exception = assertThrows(ResponseException.class, () -> ingestModelData());
        org.hamcrest.MatcherAssert
            .assertThat(
                exception.getMessage(),
                allOf(
                    containsString("Memory Circuit Breaker is open, please check your resources!"),
                    containsString("m_l_limit_exceeded_exception")
                )
            );

        // set a higher threshold
        Response response2 = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.jvm_heap_memory_threshold\":100}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response2.getStatusLine().getStatusCode());

        // expect task success
        ingestModelData();
    }
}

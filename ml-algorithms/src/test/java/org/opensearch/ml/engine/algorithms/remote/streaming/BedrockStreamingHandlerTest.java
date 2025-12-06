/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.transport.MLTaskResponse;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class BedrockStreamingHandlerTest {

    @Mock
    private SdkAsyncHttpClient mockHttpClient;

    @Mock
    private AwsConnector mockConnector;

    @Mock
    private StreamPredictActionListener<MLTaskResponse, ?> mockListener;

    private BedrockStreamingHandler handler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnector.getAccessKey()).thenReturn("testAccessKey");
        when(mockConnector.getSecretKey()).thenReturn("testSecretKey");
        when(mockConnector.getRegion()).thenReturn("us-west-2");
    }

    @Test
    public void testDefaultRetryConfiguration() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        // Use reflection to verify default values
        Field maxRetriesField = BedrockStreamingHandler.class.getDeclaredField("maxRetries");
        maxRetriesField.setAccessible(true);
        assertEquals(3, maxRetriesField.getInt(handler));

        Field initialBackoffField = BedrockStreamingHandler.class.getDeclaredField("initialBackoffMs");
        initialBackoffField.setAccessible(true);
        assertEquals(1000L, initialBackoffField.getLong(handler));

        Field maxBackoffField = BedrockStreamingHandler.class.getDeclaredField("maxBackoffMs");
        maxBackoffField.setAccessible(true);
        assertEquals(10000L, maxBackoffField.getLong(handler));
    }

    @Test
    public void testCustomRetryConfiguration() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("max_retry_times", "5");
        parameters.put("retry_backoff_millis", "2000");
        parameters.put("max_backoff_millis", "20000");

        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, parameters);

        Field maxRetriesField = BedrockStreamingHandler.class.getDeclaredField("maxRetries");
        maxRetriesField.setAccessible(true);
        assertEquals(5, maxRetriesField.getInt(handler));

        Field initialBackoffField = BedrockStreamingHandler.class.getDeclaredField("initialBackoffMs");
        initialBackoffField.setAccessible(true);
        assertEquals(2000L, initialBackoffField.getLong(handler));

        Field maxBackoffField = BedrockStreamingHandler.class.getDeclaredField("maxBackoffMs");
        maxBackoffField.setAccessible(true);
        assertEquals(20000L, maxBackoffField.getLong(handler));
    }

    @Test
    public void testInvalidRetryConfigurationFallsBackToDefault() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("max_retry_times", "invalid");
        parameters.put("retry_backoff_millis", "not_a_number");

        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, parameters);

        Field maxRetriesField = BedrockStreamingHandler.class.getDeclaredField("maxRetries");
        maxRetriesField.setAccessible(true);
        assertEquals(3, maxRetriesField.getInt(handler)); // Default value

        Field initialBackoffField = BedrockStreamingHandler.class.getDeclaredField("initialBackoffMs");
        initialBackoffField.setAccessible(true);
        assertEquals(1000L, initialBackoffField.getLong(handler)); // Default value
    }

    @Test
    public void testIsThrottlingError_TooManyConnections() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method isThrottlingError = BedrockStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        isThrottlingError.setAccessible(true);

        Throwable error = new RuntimeException("Too many connections, please wait before trying again.");
        assertTrue((Boolean) isThrottlingError.invoke(handler, error));
    }

    @Test
    public void testIsThrottlingError_StatusCode503() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method isThrottlingError = BedrockStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        isThrottlingError.setAccessible(true);

        Throwable error = new RuntimeException("Service error (Status Code: 503, Request ID: abc123)");
        assertTrue((Boolean) isThrottlingError.invoke(handler, error));
    }

    @Test
    public void testIsThrottlingError_ServiceUnavailableException() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method isThrottlingError = BedrockStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        isThrottlingError.setAccessible(true);

        Throwable error = new RuntimeException("ServiceUnavailableException: The service is temporarily unavailable");
        assertTrue((Boolean) isThrottlingError.invoke(handler, error));
    }

    @Test
    public void testIsThrottlingError_Throttling() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method isThrottlingError = BedrockStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        isThrottlingError.setAccessible(true);

        Throwable error = new RuntimeException("Request throttling detected");
        assertTrue((Boolean) isThrottlingError.invoke(handler, error));
    }

    @Test
    public void testIsThrottlingError_TooManyRequestsException() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method isThrottlingError = BedrockStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        isThrottlingError.setAccessible(true);

        Throwable error = new RuntimeException("TooManyRequestsException: Rate limit exceeded");
        assertTrue((Boolean) isThrottlingError.invoke(handler, error));
    }

    @Test
    public void testIsThrottlingError_RateExceeded() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method isThrottlingError = BedrockStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        isThrottlingError.setAccessible(true);

        Throwable error = new RuntimeException("Rate exceeded for the API");
        assertTrue((Boolean) isThrottlingError.invoke(handler, error));
    }

    @Test
    public void testIsThrottlingError_NotThrottling() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method isThrottlingError = BedrockStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        isThrottlingError.setAccessible(true);

        Throwable error = new RuntimeException("Invalid request format");
        assertFalse((Boolean) isThrottlingError.invoke(handler, error));
    }

    @Test
    public void testIsThrottlingError_NullMessage() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method isThrottlingError = BedrockStreamingHandler.class.getDeclaredMethod("isThrottlingError", Throwable.class);
        isThrottlingError.setAccessible(true);

        Throwable error = new RuntimeException((String) null);
        assertFalse((Boolean) isThrottlingError.invoke(handler, error));
    }

    @Test
    public void testCalculateBackoff_FirstRetry() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method calculateBackoff = BedrockStreamingHandler.class.getDeclaredMethod("calculateBackoff", int.class);
        calculateBackoff.setAccessible(true);

        // First retry (attempt 0): backoff should be between 1000 and 1500 (1000 + 0-50% jitter)
        long backoff = (Long) calculateBackoff.invoke(handler, 0);
        assertTrue("Backoff should be >= 1000", backoff >= 1000);
        assertTrue("Backoff should be <= 1500", backoff <= 1500);
    }

    @Test
    public void testCalculateBackoff_SecondRetry() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method calculateBackoff = BedrockStreamingHandler.class.getDeclaredMethod("calculateBackoff", int.class);
        calculateBackoff.setAccessible(true);

        // Second retry (attempt 1): base = 2000, with jitter up to 3000
        long backoff = (Long) calculateBackoff.invoke(handler, 1);
        assertTrue("Backoff should be >= 2000", backoff >= 2000);
        assertTrue("Backoff should be <= 3000", backoff <= 3000);
    }

    @Test
    public void testCalculateBackoff_ThirdRetry() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method calculateBackoff = BedrockStreamingHandler.class.getDeclaredMethod("calculateBackoff", int.class);
        calculateBackoff.setAccessible(true);

        // Third retry (attempt 2): base = 4000, with jitter up to 6000
        long backoff = (Long) calculateBackoff.invoke(handler, 2);
        assertTrue("Backoff should be >= 4000", backoff >= 4000);
        assertTrue("Backoff should be <= 6000", backoff <= 6000);
    }

    @Test
    public void testCalculateBackoff_CappedAtMaxBackoff() throws Exception {
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);

        Method calculateBackoff = BedrockStreamingHandler.class.getDeclaredMethod("calculateBackoff", int.class);
        calculateBackoff.setAccessible(true);

        // With default maxBackoff of 10000, attempt 5 would be 32000 without cap
        // Should be capped at 10000 + up to 50% jitter = 15000 max
        long backoff = (Long) calculateBackoff.invoke(handler, 5);
        assertTrue("Backoff should be >= 10000", backoff >= 10000);
        assertTrue("Backoff should be <= 15000 (max + jitter)", backoff <= 15000);
    }

    @Test
    public void testAGUIAgentDetection() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("agent_type", "ag_ui");

        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, parameters);

        Field isAGUIAgentField = BedrockStreamingHandler.class.getDeclaredField("isAGUIAgent");
        isAGUIAgentField.setAccessible(true);
        assertTrue((Boolean) isAGUIAgentField.get(handler));
    }

    @Test
    public void testNonAGUIAgent() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("agent_type", "other");

        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, parameters);

        Field isAGUIAgentField = BedrockStreamingHandler.class.getDeclaredField("isAGUIAgent");
        isAGUIAgentField.setAccessible(true);
        assertFalse((Boolean) isAGUIAgentField.get(handler));
    }

    @Test
    public void testConstructorWithNullParameters() {
        // Should not throw exception
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, null);
    }

    @Test
    public void testConstructorWithEmptyParameters() {
        // Should not throw exception
        handler = new BedrockStreamingHandler(mockHttpClient, mockConnector, new HashMap<>());
    }
}

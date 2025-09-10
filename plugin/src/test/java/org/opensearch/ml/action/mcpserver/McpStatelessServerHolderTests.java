/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.test.OpenSearchTestCase;

import io.modelcontextprotocol.server.McpStatelessAsyncServer;

public class McpStatelessServerHolderTests extends OpenSearchTestCase {

    @Mock
    private McpStatelessToolsHelper mcpStatelessToolsHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        // Reset the singleton state before each test to ensure test isolation.
        resetSingletonState();
        McpStatelessServerHolder.init(mcpStatelessToolsHelper);
    }

    /**
     * Resets McpStatelessServerHolder singleton state between tests.
     * Uses reflection to clear static fields, ensuring clean test isolation.
     */
    private void resetSingletonState() {
        try {
            // Reset statelessToolsHelper static field to null
            java.lang.reflect.Field statelessToolsHelperField = McpStatelessServerHolder.class.getDeclaredField("statelessToolsHelper");
            statelessToolsHelperField.setAccessible(true);
            statelessToolsHelperField.set(null, null);

            // Reset mcpStatelessAsyncServer static field to null
            java.lang.reflect.Field mcpStatelessAsyncServerField = McpStatelessServerHolder.class
                .getDeclaredField("mcpStatelessAsyncServer");
            mcpStatelessAsyncServerField.setAccessible(true);
            mcpStatelessAsyncServerField.set(null, null);

            // Reset mcpStatelessServerTransportProvider static field to null
            java.lang.reflect.Field mcpStatelessServerTransportProviderField = McpStatelessServerHolder.class
                .getDeclaredField("mcpStatelessServerTransportProvider");
            mcpStatelessServerTransportProviderField.setAccessible(true);
            mcpStatelessServerTransportProviderField.set(null, null);
        } catch (Exception e) {
            // If reflection fails, continue anyway - tests will still work but may have state pollution.
        }
    }

    @Test
    public void test_basicFunctionality() {
        // Mock autoLoadAllMcpTools to complete successfully
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mcpStatelessToolsHelper).autoLoadAllMcpTools(any());

        // Test basic functionality
        OpenSearchMcpStatelessServerTransportProvider provider = McpStatelessServerHolder.getMcpStatelessServerTransportProvider();
        McpStatelessAsyncServer server = McpStatelessServerHolder.getMcpStatelessAsyncServerInstance();

        assertNotNull("Provider should not be null", provider);
        assertNotNull("Server should not be null", server);

        // Verify autoLoadAllMcpTools was called
        verify(mcpStatelessToolsHelper, times(1)).autoLoadAllMcpTools(any());
    }

    @Test
    public void test_singletonBehavior() {
        // Mock autoLoadAllMcpTools to complete immediately
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mcpStatelessToolsHelper).autoLoadAllMcpTools(any());

        // Get multiple instances - should be the same (singleton behavior)
        OpenSearchMcpStatelessServerTransportProvider provider1 = McpStatelessServerHolder.getMcpStatelessServerTransportProvider();
        OpenSearchMcpStatelessServerTransportProvider provider2 = McpStatelessServerHolder.getMcpStatelessServerTransportProvider();
        McpStatelessAsyncServer server1 = McpStatelessServerHolder.getMcpStatelessAsyncServerInstance();
        McpStatelessAsyncServer server2 = McpStatelessServerHolder.getMcpStatelessAsyncServerInstance();

        assertSame("Transport providers should be the same instance", provider1, provider2);
        assertSame("Servers should be the same instance", server1, server2);
    }

    @Test
    public void test_errorHandling() {
        // Mock autoLoadAllMcpTools to call error callback
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("Test error"));
            return null;
        }).when(mcpStatelessToolsHelper).autoLoadAllMcpTools(any());

        // Server should still be created even if tool loading fails
        McpStatelessAsyncServer server = McpStatelessServerHolder.getMcpStatelessAsyncServerInstance();
        assertNotNull("Server should still be created even if tool loading fails", server);
    }

    @Test
    public void test_exceptionHandling() {
        // Mock autoLoadAllMcpTools to throw an exception
        doThrow(new RuntimeException("Test exception")).when(mcpStatelessToolsHelper).autoLoadAllMcpTools(any());

        // This should throw an exception because the server creation fails
        try {
            McpStatelessServerHolder.getMcpStatelessServerTransportProvider();
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertTrue("Exception should contain the original message", e.getMessage().contains("Failed to create stateless MCP server"));
        }
    }

    @Test
    public void test_getMcpStatelessServerTransportProvider_multiThreading() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(10);
        AtomicReference<OpenSearchMcpStatelessServerTransportProvider> providerAtomicReference = new AtomicReference<>();
        AtomicBoolean allSame = new AtomicBoolean(true);

        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    OpenSearchMcpStatelessServerTransportProvider provider = McpStatelessServerHolder
                        .getMcpStatelessServerTransportProvider();
                    OpenSearchMcpStatelessServerTransportProvider existing = providerAtomicReference.getAndSet(provider);
                    if (existing != null && existing != provider) {
                        allSame.set(false);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue("All threads should complete within 5 seconds", latch.await(5, TimeUnit.SECONDS));
        assertTrue("All threads should get the same provider instance", allSame.get());
        assertNotNull("Provider should not be null", providerAtomicReference.get());
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;

public class HttpStreamingHandlerTests {

    private Connector connector;
    private ConnectorClientConfig clientConfig;

    @Before
    public void setUp() {
        connector = mock(Connector.class);
        clientConfig = mock(ConnectorClientConfig.class);

        // Setup basic client config
        when(clientConfig.getConnectionTimeout()).thenReturn(30);
        when(clientConfig.getReadTimeout()).thenReturn(30);
    }

    @Test
    public void testConstructor_WithoutParameters() {
        String llmInterface = "openai_v1_chat_completions";

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig);

        assertNotNull("Handler should be created", handler);
    }

    @Test
    public void testConstructor_WithParameters() {
        String llmInterface = "openai_v1_chat_completions";
        Map<String, String> params = new HashMap<>();
        params.put("agui_thread_id", "thread_123");
        params.put("agui_run_id", "run_456");

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig, params);

        assertNotNull("Handler should be created with parameters", handler);
    }

    @Test
    public void testConstructor_WithAGUIParameters() {
        String llmInterface = "openai_v1_chat_completions";
        Map<String, String> params = new HashMap<>();
        params.put("agui_thread_id", "thread_123");

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig, params);

        assertNotNull("Handler should detect AG-UI parameters", handler);
    }

    @Test
    public void testConstructor_WithBackendToolNames() {
        String llmInterface = "openai_v1_chat_completions";
        Map<String, String> params = new HashMap<>();
        params.put("backend_tool_names", "[\"search_tool\", \"database_tool\"]");

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig, params);

        assertNotNull("Handler should handle backend tool names", handler);
    }

    @Test
    public void testConstructor_WithEmptyParameters() {
        String llmInterface = "openai_v1_chat_completions";
        Map<String, String> params = new HashMap<>();

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig, params);

        assertNotNull("Handler should handle empty parameters", handler);
    }

    @Test
    public void testConstructor_WithNullParameters() {
        String llmInterface = "openai_v1_chat_completions";

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig, null);

        assertNotNull("Handler should handle null parameters", handler);
    }

    @Test
    public void testConstructor_WithDifferentTimeouts() {
        String llmInterface = "openai_v1_chat_completions";

        // Test with short timeout
        when(clientConfig.getConnectionTimeout()).thenReturn(5);
        when(clientConfig.getReadTimeout()).thenReturn(10);
        HttpStreamingHandler handler1 = new HttpStreamingHandler(llmInterface, connector, clientConfig);
        assertNotNull("Handler should work with short timeout", handler1);

        // Test with long timeout
        when(clientConfig.getConnectionTimeout()).thenReturn(120);
        when(clientConfig.getReadTimeout()).thenReturn(300);
        HttpStreamingHandler handler2 = new HttpStreamingHandler(llmInterface, connector, clientConfig);
        assertNotNull("Handler should work with long timeout", handler2);
    }

    @Test
    public void testConstructor_WithDifferentLLMInterfaces() {
        String[] interfaces = { "openai_v1_chat_completions", "cohere", "anthropic", "bedrock" };

        for (String llmInterface : interfaces) {
            HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig);
            assertNotNull("Handler should work with interface: " + llmInterface, handler);
        }
    }

    @Test
    public void testConstructor_WithNullLLMInterface() {
        HttpStreamingHandler handler = new HttpStreamingHandler(null, connector, clientConfig);

        assertNotNull("Handler should handle null LLM interface", handler);
    }

    @Test
    public void testConstructor_WithEmptyLLMInterface() {
        HttpStreamingHandler handler = new HttpStreamingHandler("", connector, clientConfig);

        assertNotNull("Handler should handle empty LLM interface", handler);
    }

    @Test
    public void testConstructor_WithComplexParameters() {
        String llmInterface = "openai_v1_chat_completions";
        Map<String, String> params = new HashMap<>();
        params.put("agui_thread_id", "thread_complex_123");
        params.put("agui_run_id", "run_complex_456");
        params.put("backend_tool_names", "[\"tool1\", \"tool2\", \"tool3\"]");
        params.put("model_id", "gpt-4");
        params.put("temperature", "0.7");

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig, params);

        assertNotNull("Handler should handle complex parameters", handler);
    }

    @Test
    public void testConstructor_WithMultipleAGUIParameters() {
        String llmInterface = "openai_v1_chat_completions";
        Map<String, String> params = new HashMap<>();
        params.put("agui_thread_id", "thread_123");
        params.put("agui_run_id", "run_456");
        params.put("agui_messages", "[]");
        params.put("agui_tools", "[]");
        params.put("agui_context", "[]");

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig, params);

        assertNotNull("Handler should handle multiple AG-UI parameters", handler);
    }

    @Test
    public void testConstructor_WithZeroTimeout() {
        String llmInterface = "openai_v1_chat_completions";

        when(clientConfig.getConnectionTimeout()).thenReturn(0);
        when(clientConfig.getReadTimeout()).thenReturn(0);

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig);

        assertNotNull("Handler should handle zero timeout", handler);
    }

    @Test
    public void testConstructor_WithLargeTimeout() {
        String llmInterface = "openai_v1_chat_completions";

        // Use a large but reasonable timeout value (1 hour)
        when(clientConfig.getConnectionTimeout()).thenReturn(3600);
        when(clientConfig.getReadTimeout()).thenReturn(3600);

        HttpStreamingHandler handler = new HttpStreamingHandler(llmInterface, connector, clientConfig);

        assertNotNull("Handler should handle large timeout", handler);
    }
}

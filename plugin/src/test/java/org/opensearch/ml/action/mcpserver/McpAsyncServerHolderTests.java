/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.test.OpenSearchTestCase;

import io.modelcontextprotocol.server.McpAsyncServer;

public class McpAsyncServerHolderTests extends OpenSearchTestCase {

    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private McpToolsHelper mcpToolsHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        McpAsyncServerHolder.init(mlIndicesHandler, mcpToolsHelper);
    }

    public void test_getMcpServerTransportProviderInstance_multiThreading() {
        AtomicReference<OpenSearchMcpServerTransportProvider> providerAtomicReference = new AtomicReference<>();
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                OpenSearchMcpServerTransportProvider provider = McpAsyncServerHolder.getMcpServerTransportProviderInstance();
                providerAtomicReference.compareAndExchange(null, provider);
                assert providerAtomicReference.get() == provider;
            }).start();
        }
    }

    public void test_getMcpAsyncServerInstance() {
        McpAsyncServer server = McpAsyncServerHolder.getMcpAsyncServerInstance();
        assertNotNull(server);
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

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

    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
    }

    private void test_init_success() {
        McpAsyncServerHolder.init(mlIndicesHandler, mcpToolsHelper);
    }

    public void test_getMcpServerTransportProviderInstance() {
        McpAsyncServerHolder.init(mlIndicesHandler, mcpToolsHelper);
        OpenSearchMcpServerTransportProvider provider = McpAsyncServerHolder.getMcpServerTransportProviderInstance();
        assertNotNull(provider);
    }

    public void test_getMcpAsyncServerInstance() {
        McpAsyncServerHolder.init(mlIndicesHandler, mcpToolsHelper);
        McpAsyncServer server = McpAsyncServerHolder.getMcpAsyncServerInstance();
        assertNotNull(server);
    }

}

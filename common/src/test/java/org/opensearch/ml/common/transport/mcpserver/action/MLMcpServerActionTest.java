/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLMcpServerActionTest {

    @Test
    public void testMLMcpServerActionInstance() {
        assertNotNull(MLMcpServerAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/mcp/server", MLMcpServerAction.NAME);
    }
}

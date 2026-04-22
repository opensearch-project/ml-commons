/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLMcpConnectorListToolsActionTest {

    @Test
    public void testInstance() {
        assertNotNull(MLMcpConnectorListToolsAction.INSTANCE);
    }

    @Test
    public void testName() {
        assertEquals("cluster:admin/opensearch/ml/connectors/tools/list", MLMcpConnectorListToolsAction.NAME);
    }

}

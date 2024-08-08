/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLAgentDeleteActionTest {
    @Test
    public void testMLAgentDeleteActionInstance() {
        assertNotNull(MLAgentDeleteAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/agents/delete", MLAgentDeleteAction.NAME);
    }

}

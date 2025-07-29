/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLExecuteAgentActionTests {

    @Test
    public void testExecuteAgentAction() {
        assertNotNull(MLExecuteAgentAction.INSTANCE);
        assertEquals(MLExecuteAgentAction.NAME, "cluster:admin/opensearch/ml/agents/execute");
    }
}

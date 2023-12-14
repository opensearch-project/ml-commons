package org.opensearch.ml.common.transport.agent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MLAgentDeleteActionTest {
    @Test
    public void testMLAgentDeleteActionInstance() {
        assertNotNull(MLAgentDeleteAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/agents/delete", MLAgentDeleteAction.NAME);
    }

}

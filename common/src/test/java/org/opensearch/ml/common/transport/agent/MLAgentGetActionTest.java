package org.opensearch.ml.common.transport.agent;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MLAgentGetActionTest {

    @Test
    public void testMLAgentGetActionInstance() {
        assertNotNull(MLAgentGetAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/agents/get", MLAgentGetAction.NAME);
    }


}

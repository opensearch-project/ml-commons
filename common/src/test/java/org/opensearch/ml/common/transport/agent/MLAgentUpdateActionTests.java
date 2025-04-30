/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLAgentUpdateActionTests {

    @Test
    public void testInstance() {
        assertNotNull(MLAgentUpdateAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/agents/update", MLAgentUpdateAction.NAME);
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLRegisterAgentActionTest {

    @Test
    public void actionInstance() {
        assertNotNull(MLRegisterAgentAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/agents/register", MLRegisterAgentAction.NAME);
    }
}

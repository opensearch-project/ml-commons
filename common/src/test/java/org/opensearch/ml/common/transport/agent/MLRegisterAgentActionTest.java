/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MLRegisterAgentActionTest {

    @Test
    public void actionInstance() {
        assertNotNull(MLRegisterAgentAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/agents/register", MLRegisterAgentAction.NAME);
    }
}

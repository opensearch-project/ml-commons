/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.config;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MLConfigGetActionTest {

    @Test
    public void testMLAgentGetActionInstance() {
        assertNotNull(MLConfigGetAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/config/get", MLConfigGetAction.NAME);
    }


}

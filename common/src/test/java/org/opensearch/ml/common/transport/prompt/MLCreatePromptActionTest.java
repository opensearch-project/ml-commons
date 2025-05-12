/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLCreatePromptActionTest {
    @Test
    public void testMLCreatePromptActionInstance() {
        assertNotNull(MLCreatePromptAction.INSTANCE);
        assertEquals(MLCreatePromptAction.NAME, "cluster:admin/opensearch/ml/create_prompt");
    }
}

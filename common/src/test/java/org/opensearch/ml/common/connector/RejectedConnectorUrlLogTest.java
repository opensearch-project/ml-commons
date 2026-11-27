/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RejectedConnectorUrlLogTest {

    @Before
    public void resetWarnings() {
        RejectedConnectorUrlLog.resetUnpinnedOriginWarnings();
    }

    @After
    public void clearWarnings() {
        RejectedConnectorUrlLog.resetUnpinnedOriginWarnings();
    }

    @Test
    public void shouldWarnUnpinnedOrigin_oncePerConnectorAndAction() {
        assertTrue(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("embedding", "PREDICT"));
        assertFalse(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("embedding", "PREDICT"));
        assertFalse(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("embedding", "PREDICT"));
        // Another action on the same connector, and another connector with the same action, each warn on their own.
        assertTrue(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("embedding", "BATCH_PREDICT"));
        assertTrue(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("chat", "PREDICT"));
        assertFalse(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("chat", "PREDICT"));
    }

    @Test
    public void shouldWarnUnpinnedOrigin_startsOverOnceTheSetHasGrownPastItsLimit() {
        assertTrue(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("connector-0", "PREDICT"));
        for (int i = 1; i < 1_024; i++) {
            assertTrue(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("connector-" + i, "PREDICT"));
        }
        // 1,024 pairs are remembered; the first one is still a repeat.
        assertFalse(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("connector-0", "PREDICT"));
        // The 1,025th pair tips the set past its limit and empties it, so the first connector warns once more.
        assertTrue(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("connector-1024", "PREDICT"));
        assertTrue(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("connector-0", "PREDICT"));
        assertFalse(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("connector-0", "PREDICT"));
    }

    @Test
    public void warnUnpinnedOrigin_repeatsAreDroppedWithoutError() {
        RejectedConnectorUrlLog.warnUnpinnedOrigin("embedding", "PREDICT", "https://api.example.com");
        RejectedConnectorUrlLog.warnUnpinnedOrigin("embedding", "PREDICT", "https://api.example.com");
        assertFalse(RejectedConnectorUrlLog.shouldWarnUnpinnedOrigin("embedding", "PREDICT"));
    }
}

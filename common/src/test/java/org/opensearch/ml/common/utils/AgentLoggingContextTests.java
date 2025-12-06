/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;

public class AgentLoggingContextTests {

    private ThreadContext threadContext;

    @Before
    public void setup() {
        Settings settings = Settings.EMPTY;
        threadContext = new ThreadContext(settings);
    }

    @Test
    public void testSetAndGetRunId() {
        AgentLoggingContext.setContext(threadContext, "run-123", null);

        assertEquals("run-123", AgentLoggingContext.getRunId(threadContext));
        assertNull(AgentLoggingContext.getThreadId(threadContext));
    }

    @Test
    public void testSetAndGetThreadId() {
        AgentLoggingContext.setContext(threadContext, null, "thread-456");

        assertNull(AgentLoggingContext.getRunId(threadContext));
        assertEquals("thread-456", AgentLoggingContext.getThreadId(threadContext));
    }

    @Test
    public void testSetAndGetBothIds() {
        AgentLoggingContext.setContext(threadContext, "run-123", "thread-456");

        assertEquals("run-123", AgentLoggingContext.getRunId(threadContext));
        assertEquals("thread-456", AgentLoggingContext.getThreadId(threadContext));
    }

    @Test
    public void testGetLogPrefix_BothSet() {
        AgentLoggingContext.setContext(threadContext, "run-123", "thread-456");

        String prefix = AgentLoggingContext.getLogPrefix(threadContext);
        assertEquals("[run_id=run-123][thread_id=thread-456] ", prefix);
    }

    @Test
    public void testGetLogPrefix_OnlyRunId() {
        AgentLoggingContext.setContext(threadContext, "run-123", null);

        String prefix = AgentLoggingContext.getLogPrefix(threadContext);
        assertEquals("[run_id=run-123] ", prefix);
    }

    @Test
    public void testGetLogPrefix_OnlyThreadId() {
        AgentLoggingContext.setContext(threadContext, null, "thread-456");

        String prefix = AgentLoggingContext.getLogPrefix(threadContext);
        assertEquals("[thread_id=thread-456] ", prefix);
    }

    @Test
    public void testGetLogPrefix_NoneSet() {
        // No context set - should return empty string (no impact on other APIs)
        String prefix = AgentLoggingContext.getLogPrefix(threadContext);
        assertEquals("", prefix);
    }

    @Test
    public void testHasContext_True() {
        AgentLoggingContext.setContext(threadContext, "run-123", null);
        assertTrue(AgentLoggingContext.hasContext(threadContext));
    }

    @Test
    public void testHasContext_False() {
        assertFalse(AgentLoggingContext.hasContext(threadContext));
    }

    @Test
    public void testNullThreadContext() {
        // Should handle null gracefully
        assertEquals("", AgentLoggingContext.getLogPrefix(null));
        assertNull(AgentLoggingContext.getRunId(null));
        assertNull(AgentLoggingContext.getThreadId(null));
        assertFalse(AgentLoggingContext.hasContext(null));

        // Should not throw
        AgentLoggingContext.setContext(null, "run-123", "thread-456");
    }

    @Test
    public void testEmptyStringsIgnored() {
        AgentLoggingContext.setContext(threadContext, "", "");

        // Empty strings should not be set
        assertNull(AgentLoggingContext.getRunId(threadContext));
        assertNull(AgentLoggingContext.getThreadId(threadContext));
        assertEquals("", AgentLoggingContext.getLogPrefix(threadContext));
        assertFalse(AgentLoggingContext.hasContext(threadContext));
    }
}

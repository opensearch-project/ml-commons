/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.input.execute.agent.Message;

public class PostStructuredMemoryEventTest {

    @Test
    public void testConstructorAndGetters() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        List<Message> history = new ArrayList<>();
        Message msg = new Message();
        msg.setRole("user");
        history.add(msg);
        Map<String, Object> invocationState = new HashMap<>();
        invocationState.put("key", "value");

        PostStructuredMemoryEvent event = new PostStructuredMemoryEvent(context, history, invocationState);

        assertSame(context, event.getContext());
        assertSame(history, event.getRetrievedStructuredHistory());
        assertEquals("value", event.getInvocationState().get("key"));
    }

    @Test
    public void testWithEmptyHistory() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        List<Message> emptyHistory = new ArrayList<>();
        Map<String, Object> invocationState = new HashMap<>();

        PostStructuredMemoryEvent event = new PostStructuredMemoryEvent(context, emptyHistory, invocationState);

        assertNotNull(event.getRetrievedStructuredHistory());
        assertTrue(event.getRetrievedStructuredHistory().isEmpty());
    }

    @Test
    public void testWithNullInvocationState() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        List<Message> history = Collections.singletonList(new Message("user", null));

        PostStructuredMemoryEvent event = new PostStructuredMemoryEvent(context, history, null);

        assertNull(event.getInvocationState());
        assertEquals(1, event.getRetrievedStructuredHistory().size());
    }
}

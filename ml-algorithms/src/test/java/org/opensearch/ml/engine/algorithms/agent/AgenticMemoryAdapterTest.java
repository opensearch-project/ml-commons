/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.transport.client.Client;

/**
 * Unit tests for AgenticMemoryAdapter.
 */
public class AgenticMemoryAdapterTest {

    @Mock
    private Client client;

    private AgenticMemoryAdapter adapter;
    private final String memoryContainerId = "test-memory-container";
    private final String sessionId = "test-session";
    private final String ownerId = "test-owner";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new AgenticMemoryAdapter(client, memoryContainerId, sessionId, ownerId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullClient() {
        new AgenticMemoryAdapter(null, memoryContainerId, sessionId, ownerId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullMemoryContainerId() {
        new AgenticMemoryAdapter(client, null, sessionId, ownerId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptyMemoryContainerId() {
        new AgenticMemoryAdapter(client, "", sessionId, ownerId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullSessionId() {
        new AgenticMemoryAdapter(client, memoryContainerId, null, ownerId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptySessionId() {
        new AgenticMemoryAdapter(client, memoryContainerId, "", ownerId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullOwnerId() {
        new AgenticMemoryAdapter(client, memoryContainerId, sessionId, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptyOwnerId() {
        new AgenticMemoryAdapter(client, memoryContainerId, sessionId, "");
    }

    @Test
    public void testGetConversationId() {
        assertEquals(sessionId, adapter.getConversationId());
    }

    @Test
    public void testGetMemoryContainerId() {
        assertEquals(memoryContainerId, adapter.getMemoryContainerId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveTraceDataWithNullToolName() {
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);
        adapter.saveTraceData(null, "input", "output", "parent-id", 1, "action", listener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveTraceDataWithEmptyToolName() {
        @SuppressWarnings("unchecked")
        ActionListener<String> listener = mock(ActionListener.class);
        adapter.saveTraceData("", "input", "output", "parent-id", 1, "action", listener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveTraceDataWithNullListener() {
        adapter.saveTraceData("tool", "input", "output", "parent-id", 1, "action", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSaveInteractionWithNullListener() {
        adapter.saveInteraction("question", "response", null, 1, "action", null);
    }

    @Test
    public void testUpdateInteractionWithNullInteractionId() {
        @SuppressWarnings("unchecked")
        ActionListener<Void> listener = mock(ActionListener.class);
        Map<String, Object> updateFields = new HashMap<>();
        updateFields.put("response", "updated response");

        adapter.updateInteraction(null, updateFields, listener);

        // Verify that onFailure was called with IllegalArgumentException
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testUpdateInteractionWithEmptyInteractionId() {
        @SuppressWarnings("unchecked")
        ActionListener<Void> listener = mock(ActionListener.class);
        Map<String, Object> updateFields = new HashMap<>();
        updateFields.put("response", "updated response");

        adapter.updateInteraction("", updateFields, listener);

        // Verify that onFailure was called with IllegalArgumentException
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testUpdateInteractionWithNullUpdateFields() {
        @SuppressWarnings("unchecked")
        ActionListener<Void> listener = mock(ActionListener.class);

        adapter.updateInteraction("interaction-id", null, listener);

        // Verify that onFailure was called with IllegalArgumentException
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testUpdateInteractionWithEmptyUpdateFields() {
        @SuppressWarnings("unchecked")
        ActionListener<Void> listener = mock(ActionListener.class);
        Map<String, Object> updateFields = new HashMap<>();

        adapter.updateInteraction("interaction-id", updateFields, listener);

        // Verify that onFailure was called with IllegalArgumentException
        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateInteractionWithNullListener() {
        Map<String, Object> updateFields = new HashMap<>();
        updateFields.put("response", "updated response");

        adapter.updateInteraction("interaction-id", updateFields, null);
    }
}

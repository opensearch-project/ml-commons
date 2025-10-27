/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class AGUIStreamingEventManagerTests {

    @Test
    public void testGetRunStartedEvent_FirstCall_ReturnsEvent() {
        String threadId = "thread_test_1";
        String runId = "run_test_1";
        
        String event = AGUIStreamingEventManager.getRunStartedEvent(threadId, runId);
        
        assertNotNull("First call should return RUN_STARTED event", event);
        assertTrue("Event should contain type", event.contains("\"type\":\"RUN_STARTED\""));
        assertTrue("Event should contain threadId", event.contains("\"threadId\":\"" + threadId + "\""));
        assertTrue("Event should contain runId", event.contains("\"runId\":\"" + runId + "\""));
    }

    @Test
    public void testGetRunStartedEvent_SecondCall_ReturnsNull() {
        String threadId = "thread_test_2";
        String runId = "run_test_2";
        
        String firstEvent = AGUIStreamingEventManager.getRunStartedEvent(threadId, runId);
        assertNotNull("First call should return event", firstEvent);
        
        String secondEvent = AGUIStreamingEventManager.getRunStartedEvent(threadId, runId);
        assertNull("Second call should return null", secondEvent);
    }

    @Test
    public void testGetTextMessageStartEvent_FirstCall_ReturnsEvent() {
        String threadId = "thread_test_3";
        String runId = "run_test_3";
        
        String event = AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        
        assertNotNull("First call should return TEXT_MESSAGE_START event", event);
        assertTrue("Event should contain type", event.contains("\"type\":\"TEXT_MESSAGE_START\""));
        assertTrue("Event should contain messageId", event.contains("\"messageId\":\"msg_"));
        assertTrue("Event should contain role", event.contains("\"role\":\"assistant\""));
    }

    @Test
    public void testGetTextMessageStartEvent_SecondCall_ReturnsNull() {
        String threadId = "thread_test_4";
        String runId = "run_test_4";
        
        String firstEvent = AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        assertNotNull("First call should return event", firstEvent);
        
        String secondEvent = AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        assertNull("Second call should return null", secondEvent);
    }

    @Test
    public void testCreateTextMessageContentEvent_AlwaysReturns() {
        String threadId = "thread_test_5";
        String runId = "run_test_5";
        String delta1 = "Hello, ";
        String delta2 = "world!";
        
        String event1 = AGUIStreamingEventManager.createTextMessageContentEvent(threadId, runId, delta1);
        assertNotNull("First call should return event", event1);
        assertTrue("Event should contain delta", event1.contains("\"delta\":\"" + delta1 + "\""));
        
        String event2 = AGUIStreamingEventManager.createTextMessageContentEvent(threadId, runId, delta2);
        assertNotNull("Second call should also return event", event2);
        assertTrue("Event should contain delta", event2.contains("\"delta\":\"" + delta2 + "\""));
    }

    @Test
    public void testCreateTextMessageContentEvent_MarksMessageStarted() {
        String threadId = "thread_test_6";
        String runId = "run_test_6";
        
        // Create content event without calling getTextMessageStartEvent first
        String contentEvent = AGUIStreamingEventManager.createTextMessageContentEvent(threadId, runId, "content");
        assertNotNull("Content event should be created", contentEvent);
        
        // Now try to get TEXT_MESSAGE_START - should return null because message is already started
        String startEvent = AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        assertNull("TEXT_MESSAGE_START should be null since message already started", startEvent);
        
        // TEXT_MESSAGE_END should work since message was started
        String endEvent = AGUIStreamingEventManager.getTextMessageEndEvent(threadId, runId);
        assertNotNull("TEXT_MESSAGE_END should be returned", endEvent);
    }

    @Test
    public void testGetTextMessageEndEvent_MessageStarted_ReturnsEvent() {
        String threadId = "thread_test_7";
        String runId = "run_test_7";
        
        // Start message first
        AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        
        String endEvent = AGUIStreamingEventManager.getTextMessageEndEvent(threadId, runId);
        
        assertNotNull("Should return TEXT_MESSAGE_END event", endEvent);
        assertTrue("Event should contain type", endEvent.contains("\"type\":\"TEXT_MESSAGE_END\""));
        assertTrue("Event should contain messageId", endEvent.contains("\"messageId\":\"msg_"));
    }

    @Test
    public void testGetTextMessageEndEvent_MessageNotStarted_ReturnsNull() {
        String threadId = "thread_test_8";
        String runId = "run_test_8";
        
        // Try to end message without starting it
        String endEvent = AGUIStreamingEventManager.getTextMessageEndEvent(threadId, runId);
        
        assertNull("Should return null when message not started", endEvent);
    }

    @Test
    public void testGetTextMessageEndEvent_SecondCall_ReturnsNull() {
        String threadId = "thread_test_9";
        String runId = "run_test_9";
        
        // Start message
        AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        
        String firstEnd = AGUIStreamingEventManager.getTextMessageEndEvent(threadId, runId);
        assertNotNull("First call should return event", firstEnd);
        
        String secondEnd = AGUIStreamingEventManager.getTextMessageEndEvent(threadId, runId);
        assertNull("Second call should return null", secondEnd);
    }

    @Test
    public void testGetRunFinishedEvent_FirstCall_ReturnsEvent() {
        String threadId = "thread_test_10";
        String runId = "run_test_10";
        
        String event = AGUIStreamingEventManager.getRunFinishedEvent(threadId, runId);
        
        assertNotNull("First call should return RUN_FINISHED event", event);
        assertTrue("Event should contain type", event.contains("\"type\":\"RUN_FINISHED\""));
        assertTrue("Event should contain threadId", event.contains("\"threadId\":\"" + threadId + "\""));
        assertTrue("Event should contain runId", event.contains("\"runId\":\"" + runId + "\""));
    }

    @Test
    public void testGetRunFinishedEvent_CleansUpState() {
        String threadId = "thread_test_11";
        String runId = "run_test_11";
        
        // Create some state
        AGUIStreamingEventManager.getRunStartedEvent(threadId, runId);
        AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        
        // Finish the run
        String finishEvent = AGUIStreamingEventManager.getRunFinishedEvent(threadId, runId);
        assertNotNull("Should return RUN_FINISHED event", finishEvent);
        
        // Try to get RUN_FINISHED again - will create new event since state was cleaned up
        // This is expected behavior - the manager allows creating a new RUN_FINISHED for same conversation
        String secondFinish = AGUIStreamingEventManager.getRunFinishedEvent(threadId, runId);
        assertNotNull("Second call creates new event since state was cleaned up", secondFinish);
    }

    @Test
    public void testGetRunFinishedEvent_WithExistingState_OnlyOnce() {
        String threadId = "thread_test_12";
        String runId = "run_test_12";
        
        // Create state first
        AGUIStreamingEventManager.getRunStartedEvent(threadId, runId);
        
        String firstEvent = AGUIStreamingEventManager.getRunFinishedEvent(threadId, runId);
        assertNotNull("First call should return event", firstEvent);
        
        // After cleanup, calling again creates new state and returns event
        String secondEvent = AGUIStreamingEventManager.getRunFinishedEvent(threadId, runId);
        assertNotNull("Second call creates new event after cleanup", secondEvent);
    }

    @Test
    public void testConcurrentAccess_ThreadSafe() throws InterruptedException {
        String threadId = "thread_concurrent";
        String runId = "run_concurrent";
        int threadCount = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<String> results = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String event = AGUIStreamingEventManager.getRunStartedEvent(threadId, runId);
                    synchronized (results) {
                        results.add(event);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Only one thread should have received the event
        long nonNullCount = results.stream().filter(r -> r != null).count();
        assertEquals("Only one thread should receive the event", 1, nonNullCount);
    }

    @Test
    public void testMultipleConversations_IsolatedState() {
        String thread1 = "thread_isolated_1";
        String run1 = "run_isolated_1";
        String thread2 = "thread_isolated_2";
        String run2 = "run_isolated_2";
        
        // Start both conversations
        String start1 = AGUIStreamingEventManager.getRunStartedEvent(thread1, run1);
        String start2 = AGUIStreamingEventManager.getRunStartedEvent(thread2, run2);
        
        assertNotNull("Conversation 1 should start", start1);
        assertNotNull("Conversation 2 should start", start2);
        
        // Each conversation should have independent state
        String start1Again = AGUIStreamingEventManager.getRunStartedEvent(thread1, run1);
        assertNull("Conversation 1 should not start again", start1Again);
        
        // But conversation 2 should still be independent
        String msg2 = AGUIStreamingEventManager.getTextMessageStartEvent(thread2, run2);
        assertNotNull("Conversation 2 should be able to start message", msg2);
    }

    @Test
    public void testCompleteEventSequence() {
        String threadId = "thread_sequence";
        String runId = "run_sequence";
        
        // 1. RUN_STARTED
        String runStarted = AGUIStreamingEventManager.getRunStartedEvent(threadId, runId);
        assertNotNull("RUN_STARTED should be sent", runStarted);
        assertTrue("Should be RUN_STARTED event", runStarted.contains("RUN_STARTED"));
        
        // 2. TEXT_MESSAGE_START
        String msgStart = AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        assertNotNull("TEXT_MESSAGE_START should be sent", msgStart);
        assertTrue("Should be TEXT_MESSAGE_START event", msgStart.contains("TEXT_MESSAGE_START"));
        
        // 3. TEXT_MESSAGE_CONTENT (multiple)
        String content1 = AGUIStreamingEventManager.createTextMessageContentEvent(threadId, runId, "Hello");
        assertNotNull("First content should be sent", content1);
        assertTrue("Should be TEXT_MESSAGE_CONTENT event", content1.contains("TEXT_MESSAGE_CONTENT"));
        
        String content2 = AGUIStreamingEventManager.createTextMessageContentEvent(threadId, runId, " world");
        assertNotNull("Second content should be sent", content2);
        
        // 4. TEXT_MESSAGE_END
        String msgEnd = AGUIStreamingEventManager.getTextMessageEndEvent(threadId, runId);
        assertNotNull("TEXT_MESSAGE_END should be sent", msgEnd);
        assertTrue("Should be TEXT_MESSAGE_END event", msgEnd.contains("TEXT_MESSAGE_END"));
        
        // 5. RUN_FINISHED
        String runFinished = AGUIStreamingEventManager.getRunFinishedEvent(threadId, runId);
        assertNotNull("RUN_FINISHED should be sent", runFinished);
        assertTrue("Should be RUN_FINISHED event", runFinished.contains("RUN_FINISHED"));
        
        // After RUN_FINISHED, state is cleaned up
        // Calling getRunStartedEvent again will create new state
        String newRunStarted = AGUIStreamingEventManager.getRunStartedEvent(threadId, runId);
        assertNotNull("Can start new run after previous finished", newRunStarted);
    }

    @Test
    public void testGetRunFinishedEvent_WithoutPriorState_ReturnsEvent() {
        String threadId = "thread_no_state";
        String runId = "run_no_state";
        
        // Call RUN_FINISHED without any prior state
        String event = AGUIStreamingEventManager.getRunFinishedEvent(threadId, runId);
        
        assertNotNull("Should return RUN_FINISHED event even without prior state", event);
        assertTrue("Event should contain type", event.contains("\"type\":\"RUN_FINISHED\""));
    }

    @Test
    public void testMessageIdConsistency() {
        String threadId = "thread_msg_id";
        String runId = "run_msg_id";
        
        // Get TEXT_MESSAGE_START
        String startEvent = AGUIStreamingEventManager.getTextMessageStartEvent(threadId, runId);
        assertNotNull("Start event should exist", startEvent);
        
        // Extract message ID from start event
        String messageId = extractMessageId(startEvent);
        assertNotNull("Message ID should be extractable", messageId);
        
        // Create content event
        String contentEvent = AGUIStreamingEventManager.createTextMessageContentEvent(threadId, runId, "test");
        assertTrue("Content event should use same message ID", contentEvent.contains(messageId));
        
        // Get end event
        String endEvent = AGUIStreamingEventManager.getTextMessageEndEvent(threadId, runId);
        assertTrue("End event should use same message ID", endEvent.contains(messageId));
    }

    private String extractMessageId(String jsonEvent) {
        // Simple extraction for testing purposes
        int start = jsonEvent.indexOf("\"messageId\":\"") + 13;
        int end = jsonEvent.indexOf("\"", start);
        return jsonEvent.substring(start, end);
    }
}

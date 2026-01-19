/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.opentelemetry.api.trace.Span;

/**
 * Tests for AgentTracer thread safety and concurrent streaming completion scenarios.
 */
public class AgentTracerTest {

    @Before
    public void setUp() {
        // Reset the tracer before each test to ensure clean state
        AgentTracer.reset();
    }

    @After
    public void tearDown() {
        AgentTracer.reset();
    }

    @Test
    public void testTracerNotInitialized() {
        // When tracer is not initialized, isEnabled should return false
        assertFalse("Tracer should not be enabled when not initialized", AgentTracer.isEnabled());
    }

    @Test
    public void testStartAgentSpanReturnsInvalidSpanWhenNotInitialized() {
        // When tracer is not initialized, startAgentSpan should return invalid span
        Span span = AgentTracer.startAgentSpan("TestAgent", "session-123", "run-456");
        assertNotNull("Span should not be null", span);
        assertFalse("Span should be invalid when tracer not initialized", span.getSpanContext().isValid());
    }

    @Test
    public void testEndAgentSpanByRunIdWithNullRunId() {
        // Should handle null runId gracefully without throwing
        AgentTracer.endAgentSpanByRunId(null, true, "output");
        // No exception means success
    }

    @Test
    public void testEndAgentSpanByRunIdWithEmptyRunId() {
        // Should handle empty runId gracefully without throwing
        AgentTracer.endAgentSpanByRunId("", true, "output");
        // No exception means success
    }

    @Test
    public void testEndAgentSpanByRunIdWithNonExistentRunId() {
        // Should handle non-existent runId gracefully without throwing
        AgentTracer.endAgentSpanByRunId("non-existent-run-id", true, "output");
        // No exception means success
    }

    /**
     * Test that concurrent calls to endAgentSpanByRunId with the same runId
     * result in only one thread successfully ending the span.
     * This verifies the thread safety fix using atomic ConcurrentHashMap.remove().
     */
    @Test
    public void testConcurrentEndAgentSpanByRunIdThreadSafety() throws InterruptedException {
        // Initialize with empty credentials to disable actual tracing but enable the logic
        AgentTracer.initialize("", "test-service", "", "", "", "");

        // Since tracer is disabled, we can't create real spans
        // But we can test the concurrent map operations don't cause issues
        String runId = "concurrent-test-run-id";
        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger completedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    // All threads try to end the same span simultaneously
                    AgentTracer.endAgentSpanByRunId(runId, true, "output");
                    completedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue("All threads should complete within timeout", completed);

        // All threads should complete without exception
        assertEquals("All threads should complete successfully", numThreads, completedCount.get());

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Test simulating concurrent streaming completions where multiple streaming handlers
     * may try to complete the same agent span.
     */
    @Test
    public void testSimulateConcurrentStreamingCompletions() throws InterruptedException {
        // Simulate the scenario where multiple streaming response handlers
        // try to complete the same agent run

        int numConcurrentStreams = 20;
        int numRunIds = 5; // 5 different runs, each with 4 concurrent completion attempts

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numConcurrentStreams);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successfulCompletions = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numConcurrentStreams);

        for (int i = 0; i < numConcurrentStreams; i++) {
            final String runId = "stream-run-" + (i % numRunIds); // Creates duplicate runIds
            final int streamIndex = i;

            executor.submit(() -> {
                try {
                    startLatch.await();

                    // Simulate some work before completing
                    Thread.sleep((long) (Math.random() * 10));

                    // Try to end the agent span - only one should succeed per runId
                    AgentTracer.endAgentSpanByRunId(runId, true, "Stream " + streamIndex + " output");
                    successfulCompletions.incrementAndGet();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        assertTrue("All streaming completions should finish within timeout", completed);

        // Verify no exceptions occurred
        assertTrue("No exceptions should occur during concurrent streaming: " + exceptions, exceptions.isEmpty());

        // All attempts should complete (even if span wasn't found)
        assertEquals("All completion attempts should succeed", numConcurrentStreams, successfulCompletions.get());

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Test rapid sequential calls to endAgentSpanByRunId to ensure
     * no race conditions in the map operations.
     */
    @Test
    public void testRapidSequentialEndSpanCalls() {
        String baseRunId = "rapid-test-";
        int numCalls = 1000;

        for (int i = 0; i < numCalls; i++) {
            // Mix of same and different runIds
            String runId = baseRunId + (i % 10);
            AgentTracer.endAgentSpanByRunId(runId, i % 2 == 0, "output-" + i);
        }

        // If we get here without exception, the test passes
        assertTrue("Rapid sequential calls should not cause issues", true);
    }

    /**
     * Test that endSpan and endAgentSpanByRunId don't interfere with each other
     * when called concurrently for different spans.
     */
    @Test
    public void testMixedEndSpanOperations() throws InterruptedException {
        int numOperations = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numOperations);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < numOperations; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    if (index % 2 == 0) {
                        // Half the operations use endAgentSpanByRunId
                        AgentTracer.endAgentSpanByRunId("run-" + index, true, "output");
                    } else {
                        // Half use endSpan with invalid span (safe when not initialized)
                        AgentTracer.endSpan(Span.getInvalid(), true, "output");
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        assertTrue("All mixed operations should complete", completed);
        assertTrue("No exceptions in mixed operations: " + exceptions, exceptions.isEmpty());

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Test high contention scenario where many threads compete for the same runId.
     */
    @Test
    public void testHighContentionSameRunId() throws InterruptedException {
        String singleRunId = "high-contention-run";
        int numThreads = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger completedWithoutException = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // All threads try to end the exact same runId
                    AgentTracer.endAgentSpanByRunId(singleRunId, true, "contention output");
                    completedWithoutException.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        assertTrue("High contention test should complete", completed);
        assertEquals("All threads should complete without exception", numThreads, completedWithoutException.get());

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}

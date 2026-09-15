/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.opensearch.test.OpenSearchTestCase;

public class QueueMemoryBudgetTests extends OpenSearchTestCase {

    public void testGettersReflectConstructorAndSetter() {
        QueueMemoryBudget budget = new QueueMemoryBudget(100L);
        assertEquals(100L, budget.getMaxBytes());
        assertEquals(0L, budget.getReservedBytes());

        budget.setMaxBytes(250L);
        assertEquals(250L, budget.getMaxBytes());
    }

    public void testTryReserveWithNonPositiveBytesAlwaysSucceeds() {
        QueueMemoryBudget budget = new QueueMemoryBudget(100L);
        // bytes == 0 and bytes < 0 hit the early-return branch and must not reserve anything.
        assertTrue(budget.tryReserve(0L));
        assertTrue(budget.tryReserve(-50L));
        assertEquals(0L, budget.getReservedBytes());
    }

    public void testTryReserveWithinLimitSucceedsAndAccumulates() {
        QueueMemoryBudget budget = new QueueMemoryBudget(100L);
        assertTrue(budget.tryReserve(40L));
        assertEquals(40L, budget.getReservedBytes());
        assertTrue(budget.tryReserve(60L));
        assertEquals(100L, budget.getReservedBytes());
    }

    public void testTryReserveExceedingRemainingFails() {
        QueueMemoryBudget budget = new QueueMemoryBudget(100L);
        assertTrue(budget.tryReserve(80L));
        // bytes (30) > limit - current (20) -> fails, reservation unchanged.
        assertFalse(budget.tryReserve(30L));
        assertEquals(80L, budget.getReservedBytes());
    }

    public void testTryReserveWhenCurrentAlreadyExceedsLimitFails() {
        QueueMemoryBudget budget = new QueueMemoryBudget(100L);
        assertTrue(budget.tryReserve(100L));
        // Shrink the budget below what is already reserved to hit the current > limit branch.
        budget.setMaxBytes(50L);
        assertFalse(budget.tryReserve(1L));
        assertEquals(100L, budget.getReservedBytes());
    }

    public void testReleaseWithPositiveBytesDecrements() {
        QueueMemoryBudget budget = new QueueMemoryBudget(100L);
        assertTrue(budget.tryReserve(70L));
        budget.release(30L);
        assertEquals(40L, budget.getReservedBytes());
    }

    public void testReleaseWithNonPositiveBytesIsNoOp() {
        QueueMemoryBudget budget = new QueueMemoryBudget(100L);
        assertTrue(budget.tryReserve(50L));
        budget.release(0L);
        budget.release(-10L);
        assertEquals(50L, budget.getReservedBytes());
    }

    public void testConcurrentReservationsNeverExceedLimit() throws InterruptedException {
        long maxBytes = 1000L;
        QueueMemoryBudget budget = new QueueMemoryBudget(maxBytes);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicLong successfulReservations = new AtomicLong();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < 500; j++) {
                            if (budget.tryReserve(10L)) {
                                successfulReservations.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        // Contention drives compareAndSet retries; reserved bytes must never overshoot the limit.
        assertEquals(successfulReservations.get() * 10L, budget.getReservedBytes());
        assertTrue(budget.getReservedBytes() <= maxBytes);
    }
}

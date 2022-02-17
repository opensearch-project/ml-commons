/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.breaker;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.monitor.jvm.JvmStats;

import static org.mockito.Mockito.when;

public class MemoryCircuitBreakerTests {

    @Mock
    JvmService jvmService;

    @Mock
    JvmStats jvmStats;

    @Mock
    JvmStats.Mem mem;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(jvmService.stats()).thenReturn(jvmStats);
        when(jvmStats.getMem()).thenReturn(mem);
        when(mem.getHeapUsedPercent()).thenReturn((short) 50);
    }

    @Test
    public void testIsOpen() {
        // default threshold 85%
        CircuitBreaker breaker = new MemoryCircuitBreaker(jvmService);
        Assert.assertFalse(breaker.isOpen());

        // custom threshold 90%
        breaker = new MemoryCircuitBreaker((short) 90, jvmService);
        Assert.assertFalse(breaker.isOpen());
    }

    @Test
    public void testIsOpen_ExceedMemoryThreshold() {
        CircuitBreaker breaker = new MemoryCircuitBreaker(jvmService);

        when(mem.getHeapUsedPercent()).thenReturn((short) 95);
        Assert.assertTrue(breaker.isOpen());
    }

    @Test
    public void testIsOpen_CustomThreshold_ExceedMemoryThreshold() {
        CircuitBreaker breaker = new MemoryCircuitBreaker((short) 90, jvmService);

        when(mem.getHeapUsedPercent()).thenReturn((short) 95);
        Assert.assertTrue(breaker.isOpen());
    }
}

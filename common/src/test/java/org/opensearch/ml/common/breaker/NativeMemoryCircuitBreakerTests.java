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
import org.opensearch.monitor.os.OsService;
import org.opensearch.monitor.os.OsStats;

import static org.mockito.Mockito.when;

public class NativeMemoryCircuitBreakerTests {

    @Mock
    OsService osService;

    @Mock
    OsStats osStats;

    @Mock
    OsStats.Mem mem;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(osService.stats()).thenReturn(osStats);
        when(osStats.getMem()).thenReturn(mem);
        when(mem.getUsedPercent()).thenReturn((short) 50);
    }

    @Test
    public void testIsOpen() {
        // default threshold 97%
        CircuitBreaker breaker = new NativeMemoryCircuitBreaker(osService);
        Assert.assertFalse(breaker.isOpen());

        // custom threshold 90%
        breaker = new NativeMemoryCircuitBreaker((short) 90, osService);
        Assert.assertFalse(breaker.isOpen());
    }

    @Test
    public void testIsOpen_ExceedMemoryThreshold() {
        CircuitBreaker breaker = new NativeMemoryCircuitBreaker(osService);

        when(mem.getUsedPercent()).thenReturn((short) 99);
        Assert.assertTrue(breaker.isOpen());
    }

    @Test
    public void testIsOpen_CustomThreshold_ExceedMemoryThreshold() {
        CircuitBreaker breaker = new NativeMemoryCircuitBreaker((short) 90, osService);

        when(mem.getUsedPercent()).thenReturn((short) 95);
        Assert.assertTrue(breaker.isOpen());
    }
}

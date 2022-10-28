/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.breaker;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.env.Environment;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.monitor.jvm.JvmStats;

import java.nio.file.Path;

import static org.mockito.Mockito.when;

public class MLCircuitBreakerServiceTests {

    @InjectMocks
    private MLCircuitBreakerService mlCircuitBreakerService;

    @Mock
    JvmService jvmService;

    @Mock
    JvmStats jvmStats;

    @Mock
    JvmStats.Mem mem;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testRegisterBreaker() {
        mlCircuitBreakerService.registerBreaker(BreakerName.MEMORY, new MemoryCircuitBreaker(jvmService));
        CircuitBreaker breaker = mlCircuitBreakerService.getBreaker(BreakerName.MEMORY);

        Assert.assertNotNull(breaker);
    }

    @Test
    public void testRegisterBreakerNull() {
        CircuitBreaker breaker = mlCircuitBreakerService.getBreaker(BreakerName.MEMORY);

        Assert.assertNull(breaker);
    }

    @Test
    public void testUnregisterBreaker() {
        mlCircuitBreakerService.registerBreaker(BreakerName.MEMORY, new MemoryCircuitBreaker(jvmService));
        CircuitBreaker breaker = mlCircuitBreakerService.getBreaker(BreakerName.MEMORY);
        Assert.assertNotNull(breaker);
        mlCircuitBreakerService.unregisterBreaker(BreakerName.MEMORY);
        breaker = mlCircuitBreakerService.getBreaker(BreakerName.MEMORY);
        Assert.assertNull(breaker);
    }

    @Test
    public void testUnregisterBreakerNull() {
        mlCircuitBreakerService.registerBreaker(BreakerName.MEMORY, new MemoryCircuitBreaker(jvmService));
        mlCircuitBreakerService.unregisterBreaker(null);
        CircuitBreaker breaker = mlCircuitBreakerService.getBreaker(BreakerName.MEMORY);
        Assert.assertNotNull(breaker);
    }

    @Test
    public void testClearBreakers() {
        mlCircuitBreakerService.registerBreaker(BreakerName.MEMORY, new MemoryCircuitBreaker(jvmService));
        CircuitBreaker breaker = mlCircuitBreakerService.getBreaker(BreakerName.MEMORY);
        Assert.assertNotNull(breaker);
        mlCircuitBreakerService.clearBreakers();
        breaker = mlCircuitBreakerService.getBreaker(BreakerName.MEMORY);
        Assert.assertNull(breaker);
    }

    @Test
    public void testInit() {
        Assert.assertNotNull(mlCircuitBreakerService.init(Path.of("/")));
    }

    @Test
    public void testIsOpen() {
        when(jvmService.stats()).thenReturn(jvmStats);
        when(jvmStats.getMem()).thenReturn(mem);
        when(mem.getHeapUsedPercent()).thenReturn((short) 50);

        mlCircuitBreakerService.registerBreaker(BreakerName.MEMORY, new MemoryCircuitBreaker(jvmService));
        Assert.assertEquals(null, mlCircuitBreakerService.checkOpenCB());

        when(mem.getHeapUsedPercent()).thenReturn((short) 90);
        Assert.assertEquals("Memory Circuit Breaker", mlCircuitBreakerService.checkOpenCB());
    }

}

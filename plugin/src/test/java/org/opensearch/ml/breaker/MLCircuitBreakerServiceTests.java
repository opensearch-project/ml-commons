/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DISK_FREE_SPACE_THRESHOLD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_JVM_HEAP_MEM_THRESHOLD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_NATIVE_MEM_THRESHOLD;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.monitor.jvm.JvmStats;
import org.opensearch.monitor.os.OsService;

public class MLCircuitBreakerServiceTests {

    @InjectMocks
    private MLCircuitBreakerService mlCircuitBreakerService;

    @Mock
    JvmService jvmService;

    @Mock
    JvmStats jvmStats;

    @Mock
    JvmStats.Mem mem;

    @Mock
    ClusterService clusterService;

    @Mock
    OsService osService;

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
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_NATIVE_MEM_THRESHOLD.getKey(), 90)
            .put(ML_COMMONS_JVM_HEAP_MEM_THRESHOLD.getKey(), 95)
            .build();
        ClusterSettings clusterSettings = new ClusterSettings(
            settings,
            new HashSet<>(
                Arrays.asList(ML_COMMONS_NATIVE_MEM_THRESHOLD, ML_COMMONS_JVM_HEAP_MEM_THRESHOLD, ML_COMMONS_DISK_FREE_SPACE_THRESHOLD)
            )
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        mlCircuitBreakerService = new MLCircuitBreakerService(jvmService, osService, settings, clusterService);
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
        Assert.assertEquals("Memory Circuit Breaker", mlCircuitBreakerService.checkOpenCB().getName());
    }

}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_JVM_HEAP_MEM_THRESHOLD;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.monitor.jvm.JvmStats;

public class MemoryCircuitBreakerTests {

    @Mock
    JvmService jvmService;

    @Mock
    JvmStats jvmStats;

    @Mock
    JvmStats.Mem mem;

    @Mock
    ClusterService clusterService;

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

    @Test
    public void testIsOpen_UpdatedByClusterSettings_ExceedMemoryThreshold() {
        ClusterSettings settingsService = new ClusterSettings(Settings.EMPTY, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
        settingsService.registerSetting(ML_COMMONS_JVM_HEAP_MEM_THRESHOLD);
        when(clusterService.getClusterSettings()).thenReturn(settingsService);

        CircuitBreaker breaker = new MemoryCircuitBreaker(Settings.builder().build(), clusterService, jvmService);

        when(mem.getHeapUsedPercent()).thenReturn((short) 90);
        Assert.assertTrue(breaker.isOpen());

        Settings.Builder newSettingsBuilder = Settings.builder();
        newSettingsBuilder.put("plugins.ml_commons.jvm_heap_memory_threshold", 95);
        settingsService.applySettings(newSettingsBuilder.build());
        Assert.assertFalse(breaker.isOpen());
    }

    @Test
    public void testIsOpen_DisableMemoryCB() {
        ClusterSettings settingsService = new ClusterSettings(Settings.EMPTY, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
        settingsService.registerSetting(ML_COMMONS_JVM_HEAP_MEM_THRESHOLD);
        when(clusterService.getClusterSettings()).thenReturn(settingsService);

        CircuitBreaker breaker = new MemoryCircuitBreaker(Settings.builder().build(), clusterService, jvmService);

        when(mem.getHeapUsedPercent()).thenReturn((short) 90);
        Assert.assertTrue(breaker.isOpen());

        when(mem.getHeapUsedPercent()).thenReturn((short) 100);
        Settings.Builder newSettingsBuilder = Settings.builder();
        newSettingsBuilder.put("plugins.ml_commons.jvm_heap_memory_threshold", 100);
        settingsService.applySettings(newSettingsBuilder.build());
        Assert.assertFalse(breaker.isOpen());
    }
}

/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_NATIVE_MEM_THRESHOLD;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.monitor.os.OsService;
import org.opensearch.monitor.os.OsStats;

public class NativeMemoryCircuitBreakerTests {

    @Mock
    ClusterService clusterService;

    @Mock
    OsService osService;

    @Mock
    OsStats osStats;

    @Mock
    OsStats.Mem mem;

    private Settings settings;
    private ClusterSettings clusterSettings;

    @Before
    public void setup() {
        settings = Settings.builder().put(ML_COMMONS_NATIVE_MEM_THRESHOLD.getKey(), 90).build();
        clusterSettings = new ClusterSettings(settings, new HashSet<>(Arrays.asList(ML_COMMONS_NATIVE_MEM_THRESHOLD)));
        MockitoAnnotations.openMocks(this);
        when(osService.stats()).thenReturn(osStats);
        when(osStats.getMem()).thenReturn(mem);
        when(mem.getUsedPercent()).thenReturn((short) 50);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
    }

    @Test
    public void testIsOpen() {
        // default threshold 90%
        CircuitBreaker breaker = new NativeMemoryCircuitBreaker(osService, settings, clusterService);
        Assert.assertFalse(breaker.isOpen());

        // custom threshold 90%
        breaker = new NativeMemoryCircuitBreaker(90, osService);
        Assert.assertFalse(breaker.isOpen());
    }

    @Test
    public void testIsOpen_ExceedMemoryThreshold() {
        CircuitBreaker breaker = new NativeMemoryCircuitBreaker(osService, settings, clusterService);

        when(mem.getUsedPercent()).thenReturn((short) 95);
        Assert.assertTrue(breaker.isOpen());
    }

    @Test
    public void testIsOpen_CustomThreshold_ExceedMemoryThreshold() {
        CircuitBreaker breaker = new NativeMemoryCircuitBreaker(90, osService);

        when(mem.getUsedPercent()).thenReturn((short) 95);
        Assert.assertTrue(breaker.isOpen());
    }
}

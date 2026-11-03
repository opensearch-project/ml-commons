/*
 *  Copyright OpenSearch Contributors
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DISK_FREE_SPACE_THRESHOLD;

import java.io.File;
import java.util.HashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.unit.ByteSizeUnit;
import org.opensearch.core.common.unit.ByteSizeValue;

public class DiskCircuitBreakerTests {
    @Mock
    ClusterService clusterService;

    @Mock
    File file;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(Settings.EMPTY, new HashSet<>(List.of(ML_COMMONS_DISK_FREE_SPACE_THRESHOLD))));
    }

    @Test
    public void test_isOpen_whenDiskFreeSpaceIsHigherThanMinValue_breakerIsNotOpen() {
        CircuitBreaker breaker = new DiskCircuitBreaker(
            Settings.builder().put(ML_COMMONS_DISK_FREE_SPACE_THRESHOLD.getKey(), new ByteSizeValue(4L, ByteSizeUnit.GB)).build(),
            clusterService,
            file
        );
        when(file.getFreeSpace()).thenReturn(5 * 1024 * 1024 * 1024L);
        Assert.assertFalse(breaker.isOpen());
    }

    @Test
    public void test_isOpen_whenDiskFreeSpaceIsLessThanMinValue_breakerIsOpen() {
        CircuitBreaker breaker = new DiskCircuitBreaker(
            Settings.builder().put(ML_COMMONS_DISK_FREE_SPACE_THRESHOLD.getKey(), new ByteSizeValue(5L, ByteSizeUnit.GB)).build(),
            clusterService,
            file
        );
        when(file.getFreeSpace()).thenReturn(4 * 1024 * 1024 * 1024L);
        Assert.assertTrue(breaker.isOpen());
    }

    @Test
    public void test_isOpen_whenDiskFreeSpaceConfiguredToZero_breakerIsNotOpen() {
        CircuitBreaker breaker = new DiskCircuitBreaker(
            Settings.builder().put(ML_COMMONS_DISK_FREE_SPACE_THRESHOLD.getKey(), new ByteSizeValue(0L, ByteSizeUnit.KB)).build(),
            clusterService,
            file
        );
        when(file.getFreeSpace()).thenReturn((long) (Math.random() * 1024 * 1024 * 1024 * 1024L));
        Assert.assertFalse(breaker.isOpen());
    }

    @Test
    public void test_getName() {
        CircuitBreaker breaker = new DiskCircuitBreaker(Settings.EMPTY, clusterService, file);
        Assert.assertEquals("Disk Circuit Breaker", breaker.getName());
    }
}

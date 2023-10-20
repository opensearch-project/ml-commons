/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_DISK_SHORTAGE_THRESHOLD;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import org.opensearch.common.settings.Settings;
import org.opensearch.cluster.service.ClusterService;

import org.opensearch.ml.common.exception.MLException;

/**
 * A circuit breaker for disk usage.
 */
public class DiskCircuitBreaker extends ThresholdCircuitBreaker<Long> {
    // TODO: make this value configurable as cluster setting
    private static final String ML_DISK_CB = "Disk Circuit Breaker";
    public static final long DEFAULT_DISK_SHORTAGE_THRESHOLD = 5L;
    private static final long GB = 1024 * 1024 * 1024;
    private String diskDir;
    private volatile Long diskShortageThreshold = 5L;

    public DiskCircuitBreaker(String diskDir) {
        super(DEFAULT_DISK_SHORTAGE_THRESHOLD);
        this.diskDir = diskDir;
    }

    public DiskCircuitBreaker(long threshold, String diskDir) {
        super(threshold);
        this.diskDir = diskDir;
    }
    public DiskCircuitBreaker(Settings settings, ClusterService clusterService, String diskDir) {
        super(DEFAULT_DISK_SHORTAGE_THRESHOLD);
        this.diskDir = diskDir;
        this.diskShortageThreshold = ML_COMMONS_DISK_SHORTAGE_THRESHOLD.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_DISK_SHORTAGE_THRESHOLD, it -> diskShortageThreshold = it);
    }

    @Override
    public String getName() {
        return ML_DISK_CB;
    }

    @Override
    public boolean isOpen() {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> {
                return (new File(diskDir).getFreeSpace() / GB) < getThreshold();  // in GB
            });
        } catch (PrivilegedActionException e) {
            throw new MLException("Failed to run disk circuit breaker");
        }
    }
}

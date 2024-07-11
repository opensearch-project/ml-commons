/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_DISK_FREE_SPACE_MIN_VALUE;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Optional;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.exception.MLException;

/**
 * A circuit breaker for disk usage.
 */
public class DiskCircuitBreaker extends ThresholdCircuitBreaker<Integer> {
    private static final String ML_DISK_CB = "Disk Circuit Breaker";
    public static final int DEFAULT_DISK_SHORTAGE_THRESHOLD = 5;
    private static final long GB = 1024 * 1024 * 1024;
    private final File diskDir;

    public DiskCircuitBreaker(Settings settings, ClusterService clusterService, File diskDir) {
        super(Optional.ofNullable(ML_COMMONS_DISK_FREE_SPACE_MIN_VALUE.get(settings)).orElse(DEFAULT_DISK_SHORTAGE_THRESHOLD));
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_DISK_FREE_SPACE_MIN_VALUE, super::setThreshold);
        this.diskDir = diskDir;
    }

    @Override
    public String getName() {
        return ML_DISK_CB;
    }

    @SuppressWarnings("removal")
    @Override
    public boolean isOpen() {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> {
                return diskDir.getFreeSpace() / GB < getThreshold();  // in GB
            });
        } catch (PrivilegedActionException e) {
            throw new MLException("Failed to run disk circuit breaker");
        }
    }
}
